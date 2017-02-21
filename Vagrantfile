Vagrant.configure(2) do |config|
   config.vm.define "testvm" do |testvm|
      testvm.vm.box = "centos/6"
      testvm.vm.hostname = "testvm"
      testvm.vm.network :private_network, ip: "192.168.33.32"
      testvm.vm.provision "ansible" do |ansible|
        ansible.playbook = "src/main/ansible/vagrant.yml"
        ansible.config_file = "src/main/ansible/ansible.cfg"
#        ansible.verbose = "vvvv"
      end
      testvm.vm.provider "virtualbox" do |vb|
        vb.gui = false
        vb.memory = 2072
        vb.cpus = 2
        vb.customize ["guestproperty", "set", :id, "--timesync-threshold", "1000"]
        vb.customize ["guestproperty", "set", :id, "--timesync-interval", "1000"]
      end
   end
end
