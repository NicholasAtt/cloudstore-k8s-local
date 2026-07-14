# CloudStore Local Kubernetes Deployment

This directory contains the IaC pipeline used to provision a local Kubernetes cluster with Multipass and deploy CloudStore.

The application source code lives in the repository `src/` directory. Kubernetes does not copy source files to the nodes: it pulls the container images from GHCR.

## CloudStore Images

```text
ghcr.io/nicholasatt/cloudstore-k8s-local-client:main
ghcr.io/nicholasatt/cloudstore-k8s-local-server:main
```

The GHCR packages must be public, unless an `imagePullSecret` is configured.

---

## Requirements

Install on the host:

- Multipass
- Terraform
- WSL Ubuntu or Linux shell with Ansible
- Docker, only if rebuilding and pushing the images

Recommended VM sizing:

```text
control-plane: 2 vCPU, 2 GB RAM, 10 GB disk
workers:       2 vCPU, 2 GB RAM, 10 GB disk
```

On constrained hosts, workers can use 1 vCPU. The control-plane should normally keep 2 vCPU.

---

## Enter the Project Directory

### PowerShell

```powershell
cd <repo-root>\k8s-multipass
```

### Linux / WSL

```bash
cd <repo-root>/k8s-multipass
```

---

## Local Configuration

Create local Terraform variables.

### PowerShell

```powershell
Copy-Item terraform\terraform.tfvars.example terraform\terraform.tfvars
```

### Linux / WSL

```bash
cp terraform/terraform.tfvars.example terraform/terraform.tfvars
```

Example host-specific values:

```hcl
worker_count = 3
multipass_network_name = "Wi-Fi"
multipass_command_timeout = 1800
```

### Multipass Terraform Provider

This project uses the `todoroff/multipass` Terraform provider instead of a more generic approach because the local cluster depends on stable and reachable VM IP addresses.

During development, the default Multipass/VirtualBox networking sometimes created VMs without a usable IP address for Ansible, or exposed only NAT/internal addresses that were not suitable for the Kubernetes cluster setup.

The `todoroff/multipass` provider allows the Multipass network to be configured explicitly through Terraform. For this reason, the host-specific bridged network is declared in `terraform.tfvars`:

```hcl
multipass_network_name = "Wi-Fi"
```

This value is intentionally local and not committed, because the network name depends on the host machine. On another host it may be different, for example `Ethernet`, `Wi-Fi`, or another interface name returned by:

```bash
multipass networks
```

Terraform then uses this network to create the VMs and generate an Ansible inventory with reachable node IPs. The deployment expects all nodes to have valid LAN IPs before Ansible starts the Kubernetes bootstrap.

### Create the SSH Key Used by Ansible

#### PowerShell

```powershell
wsl
```

#### Linux / WSL

```bash
mkdir -p ~/.ssh
test -f ~/.ssh/cloudstore_k8s || ssh-keygen -t ed25519 -f ~/.ssh/cloudstore_k8s -N "" -C cloudstore-k8s
cp ~/.ssh/cloudstore_k8s.pub terraform/id_ed25519.pub
chmod 600 ~/.ssh/cloudstore_k8s
```

### Create Local CloudStore Secrets

#### PowerShell

```powershell
wsl
```

#### Linux / WSL

```bash
cp ansible/vars/cloudstore.example.yml ansible/vars/cloudstore.local.yml
openssl rand -base64 32
```

Paste the generated value into `jwt_secret` inside:

```text
ansible/vars/cloudstore.local.yml
```

Do not commit `terraform.tfvars` or `cloudstore.local.yml`.

---

## Provision the Cluster

### PowerShell

```powershell
terraform -chdir=terraform init
terraform -chdir=terraform fmt -check
terraform -chdir=terraform validate
terraform -chdir=terraform apply -input=false -auto-approve

multipass list
```

### Linux / WSL

```bash
terraform -chdir=terraform init
terraform -chdir=terraform fmt -check
terraform -chdir=terraform validate
terraform -chdir=terraform apply -input=false -auto-approve

multipass list
```

On slow hosts or with VirtualBox-based Multipass, prefer sequential VM creation.

### PowerShell

```powershell
terraform -chdir=terraform apply -input=false -auto-approve -parallelism=1
```

### Linux / WSL

```bash
terraform -chdir=terraform apply -input=false -auto-approve -parallelism=1
```

Expected result:

```text
control-plane-1   Running   <valid-ip>
worker-1          Running   <valid-ip>
worker-2          Running   <valid-ip>
worker-3          Running   <valid-ip>
```

---

## Bootstrap Kubernetes and Deploy CloudStore

Run these commands from Linux / WSL:

```bash
ansible all -i terraform/hosts.ini -m ping

ansible-playbook -i terraform/hosts.ini ansible/00-prerequisites.yml
ansible-playbook -i terraform/hosts.ini ansible/01-control-plane.yml
ansible-playbook -i terraform/hosts.ini ansible/02-workers.yml
ansible-playbook -i terraform/hosts.ini ansible/03-label-cloudstore-nodes.yml
ansible-playbook -i terraform/hosts.ini ansible/04-prepare-cloudstore-storage.yml
ansible-playbook -i terraform/hosts.ini ansible/05-deploy-cloudstore.yml
```

The playbooks create the cluster, label the nodes, prepare local storage, deploy CloudStore and run smoke tests.

---

## Low-Resource Fallback

If the host cannot create a 2-vCPU control-plane, override locally in `terraform.tfvars`:

```hcl
control_plane_cpus = 1
worker_cpus = 1
```

Then run the control-plane playbook with the NumCPU preflight bypass.

### Linux / WSL

```bash
ansible-playbook -i terraform/hosts.ini ansible/01-control-plane.yml -e kubeadm_ignore_num_cpu=true
```

### PowerShell through WSL

```powershell
wsl bash -lc "cd <repo-root>/k8s-multipass && ansible-playbook -i terraform/hosts.ini ansible/01-control-plane.yml -e kubeadm_ignore_num_cpu=true"
```

This is only a local fallback. The recommended configuration remains 2 vCPU for the control-plane.

---

## Expected Placement

```text
worker-1 -> frontend, backend
worker-2 -> order-worker
worker-3 -> MySQL, Redis, RabbitMQ
```

The frontend is exposed through NodePort:

```text
http://<node-ip>:30501
```

The credentials used to access the application are defined in the `mysql-initdb.sql` file, which initializes the MySQL database with the default application user.


---

## Cleanup

### PowerShell

```powershell
cd <repo-root>\k8s-multipass

multipass stop --all
multipass delete --all --purge

Remove-Item .\terraform\terraform.tfstate* -Force -ErrorAction SilentlyContinue
Remove-Item .\terraform\hosts.ini -Force -ErrorAction SilentlyContinue
Remove-Item .\terraform\cloud-init.rendered.yml -Force -ErrorAction SilentlyContinue
```

### Linux / WSL

```bash
cd <repo-root>/k8s-multipass

multipass stop --all
multipass delete --all --purge

rm -f terraform/terraform.tfstate*
rm -f terraform/hosts.ini
rm -f terraform/cloud-init.rendered.yml
```

Then rerun Terraform and the Ansible playbooks.

---

## Do Not Commit

Local/generated files:

```text
terraform/terraform.tfvars
terraform/terraform.tfstate*
terraform/hosts.ini
terraform/cloud-init.rendered.yml
terraform/id_ed25519*
ansible/vars/cloudstore.local.yml
```