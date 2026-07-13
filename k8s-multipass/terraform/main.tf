terraform {
  required_version = ">= 1.5.0"

  required_providers {
    multipass = {
      source  = "todoroff/multipass"
      version = "~> 1.7"
    }

    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}

provider "multipass" {
  command_timeout = var.multipass_command_timeout
}

# Render cloud-init with the local SSH public key.
resource "local_file" "cloud_init" {
  filename = "${path.module}/cloud-init.rendered.yml"

  content = templatefile("${path.module}/cloud-init.tpl", {
    ssh_public_key = trimspace(file("${path.module}/${var.ssh_public_key_file}"))
  })
}

# Control-plane node(s).
resource "multipass_instance" "control_plane" {
  count = var.control_plane_count

  name   = "control-plane-${count.index + 1}"
  image  = var.ubuntu_image
  cpus   = var.control_plane_cpus
  memory = var.control_plane_memory
  disk   = var.control_plane_disk

  cloud_init_file     = local_file.cloud_init.filename
  wait_for_cloud_init = true

  dynamic "networks" {
    for_each = var.multipass_network_name == "" ? [] : [var.multipass_network_name]

    content {
      name = networks.value
    }
  }

  depends_on = [local_file.cloud_init]
}

# Worker node(s).
resource "multipass_instance" "worker" {
  count = var.worker_count

  name   = "worker-${count.index + 1}"
  image  = var.ubuntu_image
  cpus   = var.worker_cpus
  memory = var.worker_memory
  disk   = var.worker_disk

  cloud_init_file     = local_file.cloud_init.filename
  wait_for_cloud_init = true

  dynamic "networks" {
    for_each = var.multipass_network_name == "" ? [] : [var.multipass_network_name]

    content {
      name = networks.value
    }
  }

  depends_on = [local_file.cloud_init]
}

# Select only usable IPv4 addresses for Ansible.
# We explicitly exclude:
# - N/A
# - empty values
# - VirtualBox NAT 10.0.2.x
# - link-local 169.254.x.x
locals {
  ipv4_regex = "^((25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9])$"

  control_plane_ips = [
    for cp in multipass_instance.control_plane :
    try([
      for ip in cp.ipv4 : ip
      if(
        ip != "" &&
        ip != "N/A" &&
        can(regex(local.ipv4_regex, ip)) &&
        !startswith(ip, "10.0.2.") &&
        !startswith(ip, "169.254.")
      )
    ][0], "")
  ]

  worker_ips = [
    for w in multipass_instance.worker :
    try([
      for ip in w.ipv4 : ip
      if(
        ip != "" &&
        ip != "N/A" &&
        can(regex(local.ipv4_regex, ip)) &&
        !startswith(ip, "10.0.2.") &&
        !startswith(ip, "169.254.")
      )
    ][0], "")
  ]

  node_ips = concat(local.control_plane_ips, local.worker_ips)

  invalid_node_ips = [
    for ip in local.node_ips : ip
    if(
      ip == "" ||
      ip == "N/A" ||
      !can(regex(local.ipv4_regex, ip)) ||
      startswith(ip, "10.0.2.") ||
      startswith(ip, "169.254.")
    )
  ]

  duplicated_node_ips = length(distinct(local.node_ips)) != length(local.node_ips)
}

# Fail-fast: do not generate an Ansible inventory with invalid IPs.
resource "terraform_data" "validate_node_ips" {
  input = {
    ips        = local.node_ips
    invalid    = local.invalid_node_ips
    duplicated = local.duplicated_node_ips
  }

  lifecycle {
    postcondition {
      condition = (
        length(self.input.invalid) == 0 &&
        self.input.duplicated == false
      )

      error_message = "Multipass did not expose valid unique IPv4 addresses. Configure a valid host-local Multipass network before running Ansible."
    }
  }
}

# Generate Ansible inventory only after IP validation.
resource "local_file" "inventory" {
  filename = "${path.module}/hosts.ini"

  content = templatefile("${path.module}/inventory.tpl", {
    control_plane_ips = local.control_plane_ips
    worker_ips        = local.worker_ips
    ssh_private_key   = var.ssh_private_key_path
  })

  depends_on = [terraform_data.validate_node_ips]
}