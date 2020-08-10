#ifndef SIM_WOLVERINE_TEST_HARNESS_H
#define SIM_WOLVERINE_TEST_HARNESS_H

#include <fesvr/context.h>

void wolverine_main(void *arg);
//extern "C" void wolverine_init();
//extern "C" void wolverine_tick(
//    unsigned char *disp_inst_valid,
//    int           *disp_inst_data,
//    int           *disp_reg_id,
//    unsigned char *disp_reg_read,
//    unsigned char *disp_reg_write,
//    long long     *disp_reg_wr_data,
//
//    int            disp_aeg_cnt,
//    int            disp_exception,
//    unsigned char  disp_idle,
//    unsigned char  disp_rtn_valid,
//    long long      disp_rtn_data,
//    unsigned char  disp_stall,
//
//    unsigned char *csr_wr_valid,
//    unsigned char *csr_rd_valid,
//    int           *csr_addr,
//    long long     *csr_wr_data,
//
//    unsigned char  csr_rd_ack,
//    long long      csr_rd_data,
//
//    int           *ae_id,
//    unsigned char *exit
//);


typedef struct {
    unsigned char disp_inst_valid;
    int           disp_inst_data;
    int           disp_reg_id;
    unsigned char disp_reg_read;
    unsigned char disp_reg_write;
    long long     disp_reg_wr_data;
} dispatch_req;

typedef struct {
    int            disp_aeg_cnt;
    int            disp_exception;
    unsigned char  disp_idle;
    unsigned char  disp_rtn_valid;
    long long      disp_rtn_data;
    unsigned char  disp_stall;
} dispatch_res;

typedef struct {
    unsigned char csr_wr_valid;
    unsigned char csr_rd_valid;
    int           csr_addr;
    long long     csr_wr_data;
} csr_req;

typedef struct {
    unsigned char  csr_rd_ack;
    long long      csr_rd_data;
} csr_res;

class wolverine_interface {
  public:
    wolverine_interface();
    ~wolverine_interface();

    void initHost(uint64_t mem_base);
    void writeCSR(unsigned int regInd, uint64_t regValue);
    uint64_t readCSR(unsigned int regInd);
    void targetStep(
        dispatch_req *d_req,
        dispatch_res d_res,
        csr_req *c_req,
        csr_res c_res
    );
    void hostStep();

  private:
    pthread_cond_t cond;
    pthread_mutex_t mutex;

    dispatch_req d_req;
    dispatch_res d_res;
    csr_req c_req;
    csr_res c_res;
    context_t host;
    context_t* target;
};

#endif