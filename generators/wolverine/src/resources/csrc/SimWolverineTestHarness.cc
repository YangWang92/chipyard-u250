#include <vpi_user.h>
#include <svdpi.h>
#include <vector>
#include <string>
#include <fesvr/tsi.h>
#include <assert.h>
#include <stdio.h>
#include "SimWolverineTestHarness.h"
#include <fesvr/context.h>

int cycles = 0;
bool initialized = false;
s_vpi_vlog_info info;
wolverine_interface* interface;

wolverine_interface::wolverine_interface(){
    target = context_t::current();
    host.init(wolverine_main, this);
}

wolverine_interface::~wolverine_interface(){
}

void wolverine_interface::initHost(uint64_t mem_base){
    printf("initHost 0\n");
    hostStep();
    printf("initHost 1\n");
    writeCSR(1, mem_base);
    // pseudo-dispatch
    writeCSR(0, 1);
}

void wolverine_main(void *arg){

    printf("wolverine_main\n");
    wolverine_interface *wi = static_cast<wolverine_interface*>(arg);
//    int argc = info.argc;
//    char** argv = info.argv;
//    tsi_t tsi(argc, argv);
//
//    BlockDevice *blkdev = NULL;
//    wolverine_sim_driver_t *driver;
//
//    for (int i = 1; i < argc; i++) {
//        const char *name = NULL;
//        if (strncmp(argv[i], "+blkdev=", 8) == 0) {
//            name = argv[i] + 8;
//            blkdev = new BlockDevice(name, BLKDEV_NTAGS);
//        }
//    }
//
//    driver = new wolverine_sim_driver_t(&tsi, blkdev, wi);
//
//    while(!tsi.done()){
//        driver->poll();
//    }
//
//    delete driver;
//    if (blkdev != NULL)
//        delete blkdev;
    wi->initHost(0x80000000ULL);
    while (true)
        wi->hostStep();
}

void wolverine_interface::targetStep(
    dispatch_req *d_req,
    dispatch_res d_res,
    csr_req *c_req,
    csr_res c_res
){
    // switch over to host
    host.switch_to();
    this->d_res = d_res;
    this->c_res = c_res;
    *d_req = this->d_req;
    *d_req = this->d_req;
}

void wolverine_interface::hostStep(){
    printf("hostStep 0\n");
    target->switch_to();
    //set default values
    d_req.disp_inst_valid = 0;
    d_req.disp_inst_data = 0;
    d_req.disp_reg_id = 0;
    d_req.disp_reg_read = 0;
    d_req.disp_reg_write = 0;
    d_req.disp_reg_wr_data = 0;

    c_req.csr_wr_valid = 0;
    c_req.csr_rd_valid = 0;
    c_req.csr_addr = 0;
    c_req.csr_wr_data = 0;
    printf("hostStep 1\n");
}

void wolverine_interface::writeCSR(unsigned int regInd, uint64_t regValue){
    c_req.csr_addr = regInd;
    c_req.csr_wr_valid = 1;
    c_req.csr_wr_data = regValue;
    hostStep();
}

uint64_t wolverine_interface::readCSR(unsigned int regInd){
    c_req.csr_addr = regInd;
    c_req.csr_rd_valid = 1;
    hostStep();
    assert(c_res.csr_rd_ack);
    return c_res.csr_rd_data;
}

extern "C" void wolverine_init()
{
    printf("wolverine_init\n");
    assert(!initialized);
    initialized = true;
    if (!vpi_get_vlog_info(&info)){
      printf("could not get args\n");
//      abort();
    }
    interface = new wolverine_interface();
}


extern "C" void wolverine_tick(
        unsigned char *disp_inst_valid,
        int           *disp_inst_data,
        int           *disp_reg_id,
        unsigned char *disp_reg_read,
        unsigned char *disp_reg_write,
        long long     *disp_reg_wr_data,

        int            disp_aeg_cnt,
        int            disp_exception,
        unsigned char  disp_idle,
        unsigned char  disp_rtn_valid,
        long long      disp_rtn_data,
        unsigned char  disp_stall,

        unsigned char *csr_wr_valid,
        unsigned char *csr_rd_valid,
        int           *csr_addr,
        long long     *csr_wr_data,

        unsigned char  csr_rd_ack,
        long long      csr_rd_data,

        int           *ae_id,
        unsigned char *exit
        )
{
    dispatch_req d_req;
    dispatch_res d_res;
    csr_req c_req;
    csr_res c_res;
    d_res.disp_aeg_cnt = disp_aeg_cnt;
    d_res.disp_exception = disp_exception;
    d_res.disp_idle = disp_idle;
    d_res.disp_rtn_valid = disp_rtn_valid;
    d_res.disp_rtn_data = disp_rtn_data;
    d_res.disp_stall = disp_stall;

    c_res.csr_rd_ack = csr_rd_ack;
    c_res.csr_rd_data = csr_rd_data;

    interface->targetStep(&d_req, d_res, &c_req, c_res);

    *disp_inst_valid = d_req.disp_inst_valid;
    *disp_inst_data = d_req.disp_inst_data;
    *disp_reg_id = d_req.disp_reg_id;
    *disp_reg_read = d_req.disp_reg_read;
    *disp_reg_write = d_req.disp_reg_write;
    *disp_reg_wr_data = d_req.disp_reg_wr_data;

    *csr_wr_valid = c_req.csr_wr_valid;
    *csr_rd_valid = c_req.csr_rd_valid;
    *csr_addr = c_req.csr_addr;
    *csr_wr_data = c_req.csr_wr_data;
//    printf("cycle %d\n", cycles);
    cycles++;
    *exit = cycles >= 1000;
}
