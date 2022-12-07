# Steps for compiling chipyard projects on U250

* copy the u250 board files into vivado install (Xilinx/Vivado/2021.1/data/boards/board_files/au250) - *only once per machine* 
* can be downloaded from the alveo lounge https://www.xilinx.com/member/alveo-vivado.html requires registration
* the board files include an apache licenses

## Chipyard dependencies for Centos 8 - *only once per machine*
```
sudo yum groupinstall -y "Development tools"
sudo yum install -y gmp-devel mpfr-devel libmpc-devel zlib-devel vim git java java-devel

# Install SBT https://www.scala-sbt.org/release/docs/Installing-sbt-on-Linux.html#Red+Hat+Enterprise+Linux+and+other+RPM-based+distributions
# sudo rm -f /etc/yum.repos.d/bintray-rpm.repo
# Use rm above if sbt installed from bintray before.
curl -L https://www.scala-sbt.org/sbt-rpm.repo > sbt-rpm.repo
sudo mv sbt-rpm.repo /etc/yum.repos.d/

sudo yum install -y sbt texinfo
sudo yum install -y expat-devel libusb1-devel ncurses-devel cmake "perl(ExtUtils::MakeMaker)"
# deps for poky
sudo yum install -y python38 patch diffstat texi2html texinfo subversion chrpath git wget
# deps for qemu
sudo yum install -y gtk3-devel
# deps for firemarshal
sudo yum install -y python38-pip python38-devel rsync libguestfs-tools expat ctags
# install DTC
sudo yum install -y dtc

# FireMarshal
sudo pip3 install psutil doit gitpython humanfriendly PyYAML
# Set Python3.8 as default python version:
alternatives --config python3
alternatives --config python
```

## install verilator
```
git clone http://git.veripool.org/git/verilator
cd verilator
autoconf && ./configure && make -j$(nproc) && sudo make install
cd ..
```

## clone and install Chipyard
```
git clone https://github.com/YangWang92/chipyard-u250.git
cd chipyard-u250
./scripts/init-submodules-no-riscv-tools.sh
export MAKEFLAGS=-j20
./scripts/build-toolchains.sh riscv-tools # for a normal risc-v toolchain
source env.sh

# install Firesim
cd sims/firesim
git checkout 1.51_u250
./build-setup.sh --library
unset MAKEFLAGS
source env.sh
# try it out
cd sim
make run-verilator-debug  SIM_BINARY=YOUR_RISCV_TOOLCHAINS
```

# build bitstream (fpga) for design
```
make u250-driver fpga PLATFORM=u250 PLATFORM_CONFIG=BaseF1Config1Mem


# add TARGET_CONFIG= to select a different design, PLATFORM_CONFIG=BaseF1Config1Mem_F50MHz for 50MHz clock and JAVA_HEAP_SIZE=16G for faster elaboration of big designs
make u250-driver fpga PLATFORM=u250 PLATFORM_CONFIG=BaseF1Config1Mem_F50MHz TARGET_CONFIG=WithDefaultFireSimBridges_firesim.configs.WithDefaultMemModel_WithFireSimConfigTweaks_chipyard.MegaBoomConfig JAVA_HEAP_SIZE=16G
```

# run FireSim on boards
```
cd ~/git/chipyard
source env.sh
cd sims/firesim
source env.sh
cd sim
cd output/u250/FireSim-FireSimRocketConfig-BaseF1Config1Mem/
# todo: provide sample image?
# paths to binary and block device should be absolute
# +slotid=af needs to be adjusted depending on the FPGA used.
./FireSim-u250 +permissive +mm_relaxFunctionalModel_0=0 +mm_writeMaxReqs_0=10 +mm_readMaxReqs_0=10 +mm_writeLatency_0=30 +mm_readLatency_0=30 +slotid=af +blkdev0=/cluster/home/davidcme/firesim_images/br-base.img +permissive-off /cluster/home/davidcme/firesim_images/br-base-bin
# after finishing the simulation the terminal will behave strange and needs to be reset
reset
# the bitstream needs to be re-flashed before firesim can be run again
```

# Appendix:
## reboot
```
cd /
sudo umount /cluster
sudo lustre_rmmod
sudo reboot
```

## after reboot
wait until node is up again using ping/sinfo from login node
```
sudo modprobe xdma
sudo chmod 666 /sys/bus/pci/devices/0000\:af\:00.0/resource0
sudo chmod 666 /sys/bus/pci/devices/0000\:d8\:00.0/resource0
sudo chmod 666 /dev/xdma*
```

## returning to XRT (theory so far)
* Might not be necessary because only the in-FPGA bitstream but not the ROM are changed (cold reboot should be enough)
* https://support.xilinx.com/s/article/71757?language=en_US

## server/pcie-id/device-id mapping
|  server  | pcie-id |  jtag-id   |
|----------|:-------:|------------|
|idun-06-05|    d8   |213304099003|
|idun-06-05|    af   |21330409B00K|
|idun-06-06|    d8   |21330409F03L|
|idun-06-06|    af   |21330409F028|
```
[davidcme@idun-06-05 git]$ sudo /opt/xilinx/xrt/bin/xbmgmt examine -d 0000:d8:00.0

--------------------------------------------------------
1/1 [0000:d8:00.0] : xilinx_u250_gen3x16_xdma_shell_3_1
--------------------------------------------------------
Flash properties
  Type                 : spi
  Serial Number        : 213304099003
```

```
[davidcme@idun-06-05 git]$ sudo /opt/xilinx/xrt/bin/xbmgmt examine -d 0000:af:00.0

--------------------------------------------------------
1/1 [0000:af:00.0] : xilinx_u250_gen3x16_xdma_shell_3_1
--------------------------------------------------------
Flash properties
  Type                 : spi
  Serial Number        : 21330409B00K
```

```
[davidcme@idun-06-06 ~]$ sudo /opt/xilinx/xrt/bin/xbmgmt examine -d 0000:d8:00.0

--------------------------------------------------------
1/1 [0000:d8:00.0] : xilinx_u250_gen3x16_xdma_shell_3_1
--------------------------------------------------------
Flash properties
  Type                 : spi
  Serial Number        : 21330409F03L
```

```
[davidcme@idun-06-06 ~]$ sudo /opt/xilinx/xrt/bin/xbmgmt examine -d 0000:af:00.0

--------------------------------------------------------
1/1 [0000:af:00.0] : xilinx_u250_gen3x16_xdma_shell_3_1
--------------------------------------------------------
Flash properties
  Type                 : spi
  Serial Number        : 21330409F028
```
## clean sbt (Helpful if something breaks)
```
cd ~/git/chipyard/
rm -rf ~/.ivy2/cache
rm -rf ~/.sbt
# sbt clean (adjust paths)
java -Xmx8G -Xss8M -XX:MaxPermSize=256M -Djava.io.tmpdir=/cluster/home/davidcme/git/chipyard/.java_tmp -jar /cluster/home/davidcme/git/chipyard/generators/rocket-chip/sbt-launch.jar -Dsbt.sourcemode=true -Dsbt.workspace=/cluster/home/davidcme/git/chipyard/tools -Dscala.concurrent.context.maxThreads=1 clean
```



## flash bitstream (without script)
### when switching from xilinx xrt shell for card with pcie id af:
* might always require hard reboot because of BAR config change https://stackoverflow.com/a/64254086
```
# unload xilinx driver
sudo rmmod xclmgmt
sudo rmmod xocl
# remove second physical function (.1)
# adjust af to pcie id
echo 1 | sudo tee /sys/bus/pci/devices/0000\:af\:00.1/remove > /dev/null
```

### terminal 1 - hw_server (runs as root because it needs permissions to access the usb-jtag devices)
```
sudo /cluster/projects/itea_lille-ie-idi/opt/Xilinx/Vivado/2021.1/bin/hw_server
```
### terminal 2 - xilinx pcie reset script + chmod
```
# adjust af to pcie id
/cluster/projects/itea_lille-ie-idi/env/reset_pcie_device.sh af:00.0
```
don't press c until things in terminal 3 are completed.
```
# allow non-root access to pcie bar and xdma (needs to be done after flashing)
# adjust af to pcie id
sudo chmod 666 /sys/bus/pci/devices/0000\:af\:00.0/resource0
sudo chmod 666 /dev/xdma*
```

### terminal 3 - vivado
```
source /cluster/projects/itea_lille-ie-idi/env/xilinx.sh
source /cluster/projects/itea_lille-ie-idi/env/xrt.sh
vivado
```
either in gui open the project ~/git/chipyard/sims/firesim/sim/generated-src/u250/FireSim-FireSimRocketConfig-BaseF1Config1Mem/u250/vivado_proj/firesim.xpr
* click Open Hardware Manager
* click Open Target
* select device with correct id (based on mapping in appendix) and program
  or in vivado tcl console (adjust project and target):
```
open_project ~/git/chipyard/sims/firesim/sim/generated-src/u250/FireSim-FireSimRocketConfig-BaseF1Config1Mem/u250/vivado_proj/firesim.xpr
open_hw_manager
connect_hw_server -url localhost:3121 -allow_non_jtag
# change ID to id from mapping in appendix + A
open_hw_target {localhost:3121/xilinx_tcf/Xilinx/21330409F028A}
current_hw_device [get_hw_devices xcu250_0_1]
refresh_hw_device -update_hw_probes false [lindex [get_hw_devices xcu250_0_1] 0]
set_property PROBES.FILE {~/git/chipyard/sims/firesim/sim/generated-src/u250/FireSim-FireSimRocketConfig-BaseF1Config1Mem/u250/vivado_proj/firesim.runs/impl_1/design_1_wrapper.ltx} [get_hw_devices xcu250_0]
set_property FULL_PROBES.FILE {~/git/chipyard/sims/firesim/sim/generated-src/u250/FireSim-FireSimRocketConfig-BaseF1Config1Mem/u250/vivado_proj/firesim.runs/impl_1/design_1_wrapper.ltx} [get_hw_devices xcu250_0]
set_property PROGRAM.FILE {~/git/chipyard/sims/firesim/sim/generated-src/u250/FireSim-FireSimRocketConfig-BaseF1Config1Mem/u250/vivado_proj/firesim.runs/impl_1/design_1_wrapper.bit} [get_hw_devices xcu250_0]
program_hw_devices [get_hw_devices xcu250_0]
refresh_hw_device [lindex [get_hw_devices xcu250_0] 0]
```

# TODO:
* riscv-tools and verilator shared?
* locale?
* non-sudo flashing?%    



![CHIPYARD](https://github.com/ucb-bar/chipyard/raw/master/docs/_static/images/chipyard-logo-full.png)

# Chipyard Framework [![Test](https://github.com/ucb-bar/chipyard/workflows/chipyard-ci-process/badge.svg?style=svg)](https://github.com/ucb-bar/chipyard/actions)

## Quick Links

* **STABLE DOCUMENTATION**: https://chipyard.readthedocs.io/
* **USER QUESTION FORUM**: https://groups.google.com/forum/#!forum/chipyard
* **BUGS AND FEATURE-REQUESTS**: https://github.com/ucb-bar/chipyard/issues

## Using Chipyard

To get started using Chipyard, see the stable documentation on the Chipyard documentation site: https://chipyard.readthedocs.io/

## What is Chipyard

Chipyard is an open source framework for agile development of Chisel-based systems-on-chip.
It will allow you to leverage the Chisel HDL, Rocket Chip SoC generator, and other [Berkeley][berkeley] projects to produce a [RISC-V][riscv] SoC with everything from MMIO-mapped peripherals to custom accelerators.
Chipyard contains processor cores ([Rocket][rocket-chip], [BOOM][boom], [CVA6 (Ariane)][cva6]), accelerators ([Hwacha][hwacha], [Gemmini][gemmini], [NVDLA][nvdla]), memory systems, and additional peripherals and tooling to help create a full featured SoC.
Chipyard supports multiple concurrent flows of agile hardware development, including software RTL simulation, FPGA-accelerated simulation ([FireSim][firesim]), automated VLSI flows ([Hammer][hammer]), and software workload generation for bare-metal and Linux-based systems ([FireMarshal][firemarshal]).
Chipyard is actively developed in the [Berkeley Architecture Research Group][ucb-bar] in the [Electrical Engineering and Computer Sciences Department][eecs] at the [University of California, Berkeley][berkeley].

## Resources

* Chipyard Stable Documentation: https://chipyard.readthedocs.io/
* Chipyard (x FireSim) Tutorial: https://fires.im/tutorial
* Chipyard Basics slides: https://fires.im/micro21-slides-pdf/02_chipyard_basics.pdf
* Chipyard Tutorial Exercise slides: https://fires.im/micro21-slides-pdf/03_building_custom_socs.pdf

## Need help?

* Join the Chipyard Mailing List: https://groups.google.com/forum/#!forum/chipyard
* If you find a bug or would like propose a feature, post an issue on this repo: https://github.com/ucb-bar/chipyard/issues

## Contributing

* See [CONTRIBUTING.md](/CONTRIBUTING.md)

## Attribution and Chipyard-related Publications

If used for research, please cite Chipyard by the following publication:

```
@article{chipyard,
  author={Amid, Alon and Biancolin, David and Gonzalez, Abraham and Grubb, Daniel and Karandikar, Sagar and Liew, Harrison and Magyar,   Albert and Mao, Howard and Ou, Albert and Pemberton, Nathan and Rigge, Paul and Schmidt, Colin and Wright, John and Zhao, Jerry and Shao, Yakun Sophia and Asanovi\'{c}, Krste and Nikoli\'{c}, Borivoje},
  journal={IEEE Micro},
  title={Chipyard: Integrated Design, Simulation, and Implementation Framework for Custom SoCs},
  year={2020},
  volume={40},
  number={4},
  pages={10-21},
  doi={10.1109/MM.2020.2996616},
  ISSN={1937-4143},
}
```

* **Chipyard**
    * A. Amid, et al. *IEEE Micro'20* [PDF](https://ieeexplore.ieee.org/document/9099108).
    * A. Amid, et al. *DAC'20* [PDF](https://ieeexplore.ieee.org/document/9218756).
    * A. Amid, et al. *ISCAS'21* [PDF](https://ieeexplore.ieee.org/abstract/document/9401515).

These additional publications cover many of the internal components used in Chipyard. However, for the most up-to-date details, users should refer to the Chipyard docs.

* **Generators**
    * **Rocket Chip**: K. Asanovic, et al., *UCB EECS TR*. [PDF](http://www2.eecs.berkeley.edu/Pubs/TechRpts/2016/EECS-2016-17.pdf).
    * **BOOM**: C. Celio, et al., *Hot Chips 30*. [PDF](https://old.hotchips.org/hc30/1conf/1.03_Berkeley_BROOM_HC30.Berkeley.Celio.v02.pdf).
      * **SonicBOOM (BOOMv3)**: J. Zhao, et al., *CARRV'20*. [PDF](https://carrv.github.io/2020/papers/CARRV2020_paper_15_Zhao.pdf).
      * **COBRA (BOOM Branch Prediction)**: J. Zhao, et al., *ISPASS'21*. [PDF](https://ieeexplore.ieee.org/document/9408173).
    * **Hwacha**: Y. Lee, et al., *ESSCIRC'14*. [PDF](http://hwacha.org/papers/riscv-esscirc2014.pdf).
    * **Gemmini**: H. Genc, et al., *arXiv*. [PDF](https://arxiv.org/pdf/1911.09925).
* **Sims**
    * **FireSim**: S. Karandikar, et al., *ISCA'18*. [PDF](https://sagark.org/assets/pubs/firesim-isca2018.pdf).
        * **FireSim Micro Top Picks**: S. Karandikar, et al., *IEEE Micro, Top Picks 2018*. [PDF](https://sagark.org/assets/pubs/firesim-micro-top-picks2018.pdf).
        * **FASED**: D. Biancolin, et al., *FPGA'19*. [PDF](https://people.eecs.berkeley.edu/~biancolin/papers/fased-fpga19.pdf).
        * **Golden Gate**: A. Magyar, et al., *ICCAD'19*. [PDF](https://davidbiancolin.github.io/papers/goldengate-iccad19.pdf).
        * **FirePerf**: S. Karandikar, et al., *ASPLOS'20*. [PDF](https://sagark.org/assets/pubs/fireperf-asplos2020.pdf).
* **Tools**
    * **Chisel**: J. Bachrach, et al., *DAC'12*. [PDF](https://people.eecs.berkeley.edu/~krste/papers/chisel-dac2012.pdf).
    * **FIRRTL**: A. Izraelevitz, et al., *ICCAD'17*. [PDF](https://ieeexplore.ieee.org/document/8203780).
    * **Chisel DSP**: A. Wang, et al., *DAC'18*. [PDF](https://ieeexplore.ieee.org/document/8465790).
    * **FireMarshal**: N. Pemberton, et al., *ISPASS'21*. [PDF](https://ieeexplore.ieee.org/document/9408192).
* **VLSI**
    * **Hammer**: E. Wang, et al., *ISQED'20*. [PDF](https://www.isqed.org/English/Archives/2020/Technical_Sessions/113.html).

## Acknowledgements

This work is supported by the NSF CCRI ENS Chipyard Award #201662.

[hwacha]:https://www2.eecs.berkeley.edu/Pubs/TechRpts/2015/EECS-2015-262.pdf
[hammer]:https://github.com/ucb-bar/hammer
[firesim]:https://fires.im
[ucb-bar]: http://bar.eecs.berkeley.edu
[eecs]: https://eecs.berkeley.edu
[berkeley]: https://berkeley.edu
[riscv]: https://riscv.org/
[rocket-chip]: https://github.com/freechipsproject/rocket-chip
[boom]: https://github.com/riscv-boom/riscv-boom
[firemarshal]: https://github.com/firesim/FireMarshal/
[cva6]: https://github.com/openhwgroup/cva6/
[gemmini]: https://github.com/ucb-bar/gemmini
[nvdla]: http://nvdla.org/
