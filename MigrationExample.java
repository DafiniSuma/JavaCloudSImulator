/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cloud4;

import org.cloudbus.cloudsim.allocationpolicies.migration.VmAllocationPolicyMigrationBestFitStaticThreshold;
import org.cloudbus.cloudsim.allocationpolicies.migration.VmAllocationPolicyMigrationStaticThreshold;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.selectionpolicies.power.PowerVmSelectionPolicyMinimumUtilization;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.HostHistoryTableBuilder;
import org.cloudsimplus.listeners.DatacenterBrokerEventInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.cloudbus.cloudsim.provisioners.ResourceProvisioner;
import org.cloudsimplus.listeners.EventListener;

public final class MigrationExample {
    /**
     * @see Datacenter#getSchedulingInterval()
     */
    private static final int  SCHEDULING_INTERVAL = 1;
    private static final int  HOSTS = 5;
    private static final int  VMS = 3;
    private static final int  HOST_MIPS = 1000; //for each PE
    private static final int  HOST_INITIAL_PES = 4;
    private static final long HOST_RAM = 500000; //host memory (MB)
    private static final long HOST_STORAGE = 1000000; //host storage

    /**
     * The time spent during VM migration depend on the
     * bandwidth of the target Host.
     * By default, a {@link Datacenter}
     * uses only 50% of the BW to migrate VMs, while the
     * remaining capacity is used for VM communication.
     * This can be changed by calling
     * {@link DatacenterSimple#setBandwidthPercentForMigration(double)}.
     *
     * <p>The 16000 Mb/s is the same as 2000 MB/s. Since just half of this capacity
     * is used for VM migration, only 1000 MB/s will be available for this process.
     * The time that takes to migrate a Vm depend on the VM RAM capacity.
     * Since VMs in this example are created with 2000 MB of RAM, any migration
     * will take 2 seconds to finish, as can be seen in the logs.
     */
    private static final long   HOST_BW = 16000L; //Mb/s

    /**
     * The percentage of host CPU usage that trigger VM migration
     * due to over utilization (in scale from 0 to 1, where 1 is 100%).
     */
    private static final double HOST_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION = 0.7;

    private static final int    VM_MIPS = 1000; //for each PE
    private static final long   VM_SIZE = 1000; //image size (MB)
    private static final int    VM_RAM = 10000; //VM memory (MB)
    private static final double VM_BW = HOST_BW/(double)VMS;
    private static final int    VM_PES = 2;

    private static final long   CLOUDLET_LENGHT = 20000;
    private static final long   CLOUDLET_FILESIZE = 300;
    private static final long   CLOUDLET_OUTPUTSIZE = 300;

    /**
     * The percentage of CPU that a cloudlet will use when
     * it starts executing (in scale from 0 to 1, where 1 is 100%).
     * For each cloudlet create, this value is used
     * as a base to define CPU usage.
     * @see #createAndSubmitCloudlets(DatacenterBroker)
     */
    private static final double CLOUDLET_INITIAL_CPU_PERCENTAGE = 0.8;

    /**
     * Defines the speed (in percentage) that CPU usage of a cloudlet
     * will increase during the simulation execution.
     * (in scale from 0 to 1, where 1 is 100%).
     * @see #createCpuUtilizationModel(double, double)
     */
    private static final double CLOUDLET_CPU_INCREMENT_PER_SECOND = 0.04;

    /**
     * List of all created VMs.
     */
    private final List<Vm> vmList = new ArrayList<>();
    private final DatacenterBrokerSimple broker;

    private CloudSim simulation;
    private VmAllocationPolicyMigrationStaticThreshold allocationPolicy;
    private List<Host> hostList;

    /**
     * Starts the example.
     *
     * @param args
     */
    public static void main(String[] args) {
        new MigrationExample();
    }

    public MigrationExample(){
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());
        simulation = new CloudSim();

        @SuppressWarnings("unused")
        Datacenter datacenter0 = createDatacenter();
        broker = new DatacenterBrokerSimple(simulation);
        createAndSubmitVms(broker);
        createAndSubmitCloudlets(broker);

        broker.addOnVmsCreatedListener(this::onVmsCreatedListener);

        simulation.start();

        final List<Cloudlet> finishedList = broker.getCloudletFinishedList();
        finishedList.sort(
            Comparator.comparingLong((Cloudlet c) -> c.getVm().getHost().getId())
                      .thenComparingLong(c -> c.getVm().getId()));
        new CloudletsTableBuilder(finishedList).build();
        System.out.println("\nHosts CPU usage History (when the allocated MIPS is lower than the requested, it is due to VM migration overhead)");

        hostList.stream().filter(h -> h.getId() <= 2).forEach(this::printHostHistory);
        System.out.println(getClass().getSimpleName() + " finished!");
    }

    private void printHostHistory(Host host) {
        new HostHistoryTableBuilder(host).setTitle(host.toString()).build();
    }

    public void createAndSubmitCloudlets(DatacenterBroker broker) {
        final List<Cloudlet> list = new ArrayList<>(VMS -1);
        Cloudlet cloudlet = Cloudlet.NULL;
        UtilizationModelDynamic um = createCpuUtilizationModel(CLOUDLET_INITIAL_CPU_PERCENTAGE, 1);
        for(Vm vm: vmList){
            cloudlet = createCloudlet(vm, broker, um);
            list.add(cloudlet);
        }

        //Changes the CPU usage of the last cloudlet to start at a lower value and increase dynamically up to 100%
        cloudlet.setUtilizationModelCpu(createCpuUtilizationModel(0.2, 1));

        broker.submitCloudletList(list);
    }

    /**
     * Creates a Cloudlet.
     *
     * @param vm the VM that will run the Cloudlets
     * @param broker the broker that the created Cloudlets belong to
     * @param cpuUtilizationModel the CPU UtilizationModel for the Cloudlet
     * @return the created Cloudlets
     */
    public Cloudlet createCloudlet(Vm vm, DatacenterBroker broker, UtilizationModel cpuUtilizationModel) {
        final UtilizationModel utilizationModelFull = new UtilizationModelFull();

        final Cloudlet cloudlet =
            new CloudletSimple(CLOUDLET_LENGHT, (int)vm.getNumberOfPes())
                .setFileSize(CLOUDLET_FILESIZE)
                .setOutputSize(CLOUDLET_OUTPUTSIZE)
                .setUtilizationModelRam(utilizationModelFull)
                .setUtilizationModelBw(utilizationModelFull)
                .setUtilizationModelCpu(cpuUtilizationModel);
        broker.bindCloudletToVm(cloudlet, vm);

        return cloudlet;
    }

    public void createAndSubmitVms(DatacenterBroker broker) {
        List<Vm> list = new ArrayList<>(VMS);
        for(int i = 0; i < VMS; i++){
            Vm vm = createVm(broker, VM_PES);
            list.add(vm);
        }

        vmList.addAll(list);
        broker.submitVmList(list);
    }

    public Vm createVm(DatacenterBroker broker, int pes) {
        Vm vm = new VmSimple(VM_MIPS, pes);
        vm
          .setRam(VM_RAM).setBw((long)VM_BW).setSize(VM_SIZE)
          .setCloudletScheduler(new CloudletSchedulerTimeShared());
        return vm;
    }

    /**
     * Creates a CPU UtilizationModel for a Cloudlet
     * which will always use the given initial CPU usage percentage.
     * This way, the usage will not change over the time,
     * since the max usage will be the initial usage.
     *
     * @param initialCpuUsagePercent the percentage of CPU utilization
     * that created Cloudlets will use when they start to execute.
     * If this value is greater than 1 (100%), it will be changed to 1.
     * @return
     */
    private UtilizationModelDynamic createCpuUtilizationModel(double initialCpuUsagePercent) {
        return createCpuUtilizationModel(initialCpuUsagePercent, initialCpuUsagePercent);
    }

    /**
     * Creates a CPU UtilizationModel for a Cloudlet.
     * If the initial usage is lower than the max usage, the usage will
     * be dynamically incremented along the time, according to the
     * {@link #getCpuUsageIncrement(org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic)}
     * function. Otherwise, the CPU usage will be static, according to the
     * defined initial usage.
     *
     * @param initialCpuUsagePercent the percentage of CPU utilization
     * that created Cloudlets will use when they start to execute.
     * If this value is greater than 1 (100%), it will be changed to 1.
     * @param maxCpuUsagePercentage the maximum percentage of
     * CPU utilization that created Cloudlets are allowed to use.
     * If this value is greater than 1 (100%), it will be changed to 1.
     * It must be equal or greater than the initial CPU usage.
     * @return
     */
    private UtilizationModelDynamic createCpuUtilizationModel(double initialCpuUsagePercent, double maxCpuUsagePercentage) {
        if(maxCpuUsagePercentage < initialCpuUsagePercent){
            throw new IllegalArgumentException("Max CPU usage must be equal or greater than the initial CPU usage.");
        }

        initialCpuUsagePercent = Math.min(initialCpuUsagePercent, 1);
        maxCpuUsagePercentage = Math.min(maxCpuUsagePercentage, 1);
        UtilizationModelDynamic um;
        if (initialCpuUsagePercent < maxCpuUsagePercentage) {
            um = new UtilizationModelDynamic(initialCpuUsagePercent)
                        .setUtilizationUpdateFunction(this::getCpuUsageIncrement);
        } else {
            um = new UtilizationModelDynamic(initialCpuUsagePercent);
        }

        um.setMaxResourceUtilization(maxCpuUsagePercentage);
        return um;
    }

    /**
     * Increments the CPU resource utilization, that is defined in percentage values.
     * @return the new resource utilization after the increment
     */
    private double getCpuUsageIncrement(final UtilizationModelDynamic um){
        return um.getUtilization() + um.getTimeSpan()*CLOUDLET_CPU_INCREMENT_PER_SECOND;
    }

    /**
     * Creates a Datacenter with number of Hosts defined by {@link #HOSTS},
     * but only some of these Hosts will be active (powered on) initially.
     *
     * @return
     */
    private Datacenter createDatacenter() {
        this.hostList = new ArrayList<>();
        for(int i = 0; i < HOSTS; i++){
            final int pes = HOST_INITIAL_PES + i;
            Host host = createHost(pes, HOST_MIPS);
            hostList.add(host);
        }
        System.out.println();

        /**
         * Sets an upper utilization threshold higher than the
         * {@link #HOST_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION}
         * to enable placing VMs which will use more CPU than
         * defined by the value in the mentioned constant.
         * After VMs are all submitted to Hosts, the threshold is changed
         * to the value of the constant.
         * This is used to  place VMs into a Host which will
         * become overloaded in order to trigger the migration.
         */
        this.allocationPolicy =
            new VmAllocationPolicyMigrationBestFitStaticThreshold(
                new PowerVmSelectionPolicyMinimumUtilization(),
                HOST_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION+0.2);

        DatacenterSimple dc = new DatacenterSimple(simulation, hostList, allocationPolicy);
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        return dc;
    }

    public Host createHost(int numberOfPes, long mipsByPe) {
            List<Pe> peList = createPeList(numberOfPes, mipsByPe);
            Host host =
                new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
            host
                .setRamProvisioner((ResourceProvisioner) new ResourceProvisionerSimple())
                .setBwProvisioner((ResourceProvisioner) new ResourceProvisionerSimple())
                .setVmScheduler(new VmSchedulerTimeShared());
            host.enableStateHistory();
            return host;
    }

    public List<Pe> createPeList(int numberOfPEs, long mips) {
        List<Pe> list = new ArrayList<>(numberOfPEs);
        for(int i = 0; i < numberOfPEs; i++) {
            list.add(new PeSimple(mips, new PeProvisionerSimple()));
        }
        return list;
    }


    /**
     * A listener that is called after all VMs from a broker are created,
     * setting the allocation policy to the default value
     * so that some Hosts will be overloaded with the placed VMs and migration will be fired.
     *
     * The listener is removed after finishing, so that it's called just once,
     * even if new VMs are submitted and created latter on.
    */
    private void onVmsCreatedListener(final DatacenterBrokerEventInfo evt) {
        allocationPolicy.setOverUtilizationThreshold(HOST_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION);
        broker.addOnVmsCreatedListener((EventListener<DatacenterBrokerEventInfo>) evt.getListener());
    }
}

