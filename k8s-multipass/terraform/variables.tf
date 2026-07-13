variable "ubuntu_image" {
  description = "Ubuntu image tag recognised by Multipass (e.g. '24.04', 'lts')"
  type        = string
  default     = "24.04"
}

# Control-plane

variable "control_plane_count" {
  description = "Number of control-plane nodes (1 for a standard single-master setup)"
  type        = number
  default     = 1
}

variable "control_plane_cpus" {
  description = "vCPUs allocated to each control-plane node"
  type        = number
  default     = 1
}

variable "control_plane_memory" {
  description = "RAM allocated to each control-plane node (kubeadm requires >= 2 GB)"
  type        = string
  default     = "2G"
}

variable "control_plane_disk" {
  description = "Disk size for each control-plane node"
  type        = string
  default     = "10G"
}

# Workers

variable "worker_count" {
  description = "Number of Kubernetes worker nodes"
  type        = number
  default     = 3
}

variable "worker_cpus" {
  description = "vCPUs allocated to each worker node"
  type        = number
  default     = 1
}

variable "worker_memory" {
  description = "RAM allocated to each worker node"
  type        = string
  default     = "2G"
}

variable "worker_disk" {
  description = "Disk size for each worker node"
  type        = string
  default     = "10G"
}

# Ansible

variable "ssh_public_key_file" {
  description = "Public SSH key file name, relative to the Terraform module directory"
  type        = string
  default     = "id_ed25519.pub"
}

variable "ssh_private_key_path" {
  description = "Path to the SSH private key used by Ansible on the Ansible controller"
  type        = string
  default     = "~/.ssh/cloudstore_k8s"
}

variable "multipass_network_name" {
  description = "Optional host-local Multipass network name. Leave empty for default networking. Set locally, for example 'Wi-Fi' on Windows Home with VirtualBox."
  type        = string
  default     = ""
}

variable "multipass_command_timeout" {
  description = "Timeout in seconds for Multipass commands. Increase on slow local hosts."
  type        = number
  default     = 1800
}
