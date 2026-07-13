output "control_plane_ips" {
  description = "Usable IPv4 addresses of the control-plane node(s)"
  value       = local.control_plane_ips
}

output "worker_ips" {
  description = "Usable IPv4 addresses of the worker nodes"
  value       = local.worker_ips
}

output "next_steps" {
  description = "Ansible commands to run after apply"
  value = {
    multipass_list = "multipass list"
    ping           = "ansible all -i terraform/hosts.ini -m ping"
    prerequisites  = "ansible-playbook -i terraform/hosts.ini ansible/00-prerequisites.yml"
    control_plane  = "ansible-playbook -i terraform/hosts.ini ansible/01-control-plane.yml"
    workers        = "ansible-playbook -i terraform/hosts.ini ansible/02-workers.yml"
    labels         = "ansible-playbook -i terraform/hosts.ini ansible/03-label-cloudstore-nodes.yml"
    storage        = "ansible-playbook -i terraform/hosts.ini ansible/04-prepare-cloudstore-storage.yml"
    cloudstore     = "ansible-playbook -i terraform/hosts.ini ansible/05-deploy-cloudstore.yml"
    nodes          = "ansible control_plane -i terraform/hosts.ini -m command -a \"kubectl get nodes -o wide\""
    destroy_vms    = "terraform -chdir=terraform destroy"
  }
}
