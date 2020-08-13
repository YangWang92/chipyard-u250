#include <stdio.h>
#include <stdlib.h>
#include <deque>
#include <assert.h>

#define MEM_SIZE 0x40000000ULL
#define MEM_BASE 0x20000000ULL
#define MEM_CYCLES 10

typedef struct{
    int cycle;
    int cmd;
    int scmd;
    int size;
    uint64_t addr;
    int rtnctl;
    uint64_t data;
} mem_request;

char * mem = NULL;
int beat = 0;
int cycle = 0;
std::deque<mem_request> * queue = NULL;

extern "C" void wolverine_mem_tick(
        unsigned char  mc_req_valid,
        int            mc_req_rtnctl,
        long long      mc_req_data,
        long long      mc_req_addr,
        int            mc_req_size,
        int            mc_req_cmd,
        int            mc_req_scmd,
        unsigned char *mc_req_stall,

        unsigned char *mc_res_valid,
        int           *mc_res_cmd,
        int           *mc_res_scmd,
        long long     *mc_res_data,
        int           *mc_res_rtnctl,
        unsigned char  mc_res_stall,

        unsigned char  mc_req_flush,
        unsigned char *mc_res_flush_ok
        )
{
    if(!mem){
        printf("allocating mem buffer\n");
        mem = (char *)malloc(MEM_SIZE);
    }
    if(!queue){
        queue = new std::deque<mem_request>();
    }
    if(mc_req_valid){
        mem_request mr;
        mr.cycle = cycle+MEM_CYCLES;
        mr.cmd = mc_req_cmd;
        mr.scmd = mc_req_scmd;
        mr.size = mc_req_size;
        mr.addr = mc_req_addr;
        mr.rtnctl = mc_req_rtnctl;
        mr.data = mc_req_data;
//        printf("MEMORY REQUEST:\n");
//        printf("mr.cycle: %d\n", mr.cycle);
//        printf("mr.cmd: %d\n", mr.cmd);
//        printf("mr.scmd: %d\n", mr.scmd);
//        printf("mr.size: %d\n", mr.size);
//        printf("mr.addr: %lx\n", mr.addr);
//        printf("mr.rtnctl: %d\n", mr.rtnctl);
//        printf("mr.data: %lx\n", mr.data);
        queue->push_back(mr);
    }
    // never stall for now
    *mc_req_stall = 0;

    *mc_res_valid = 0;
    *mc_res_cmd = 0;
    *mc_res_scmd = 0;
    *mc_res_data = 0;
    *mc_res_rtnctl = 0;

    if((!queue->empty()) && queue->front().cycle <=cycle){
        mem_request mr = queue->front();

//        printf("MEMORY PROCESSING:\n");
//        printf("mr.cycle: %d\n", mr.cycle);
//        printf("mr.cmd: %d\n", mr.cmd);
//        printf("mr.scmd: %d\n", mr.scmd);
//        printf("mr.size: %d\n", mr.size);
//        printf("mr.addr: %lx\n", mr.addr);
//        printf("mr.rtnctl: %d\n", mr.rtnctl);
//        printf("mr.data: %lx\n", mr.data);
        uint64_t addr = mr.addr - MEM_BASE;
        assert(addr<MEM_SIZE);
        if(mr.cmd == 2){ // write
            assert(mr.scmd == 0);
            queue->pop_front();
            if(mr.size == 2){
                for(int i=0; i<4; i++){
                    mem[addr+i] = (mr.data >> (8*i)) & 0xFF;
                }
            } else if(mr.size == 3){
                for(int i=0; i<8; i++){
                    mem[addr+i] = (mr.data >> (8*i)) & 0xFF;
                }
            } else {
                assert(false);
            }
            *mc_res_valid = 1;
            *mc_res_cmd = 3;
            *mc_res_scmd = 0;
            *mc_res_data = 0;
            *mc_res_rtnctl = mr.rtnctl;
        } else if(mr.cmd == 1){ // read
            assert(mr.scmd == 0);
            queue->pop_front();
            assert(mr.size == 3);
            uint64_t dt = 0;
            for(int i=0; i<8; i++){
                dt |= (mem[addr+i]&0xFFULL) << (i*8);
            }
            *mc_res_valid = 1;
            *mc_res_cmd = 2;
            *mc_res_scmd = 0;
            *mc_res_data = dt;
            *mc_res_rtnctl = mr.rtnctl;
        } else if(mr.cmd == 6){ // multi write
            queue->pop_front();
            assert(mr.size == 3);
            int beats = (mr.scmd == 0) ? 8 : mr.scmd;
            for(int i=0; i<8; i++){
                mem[addr+i] = (mr.data >> (8*i)) & 0xFF;
            }
            beat++;
            if(beat == beats){
                beat = 0;
                *mc_res_valid = 1;
                *mc_res_cmd = 3;
                *mc_res_scmd = 0;
                *mc_res_data = 0;
                *mc_res_rtnctl = mr.rtnctl;
            }
        } else if(mr.cmd == 7){ // multi read
            assert(mr.scmd == 0);
            assert(mr.size==3);
            uint64_t dt = 0;
            for(int i=0; i<8; i++){
                dt |= (mem[addr+i+beat*8]&0xFFULL) << (i*8);
            }
            *mc_res_valid = 1;
            *mc_res_cmd = 7;
            *mc_res_scmd = beat;
            *mc_res_data = dt;
            *mc_res_rtnctl = mr.rtnctl;
            beat++;
            if(beat==8){
                beat = 0;
                queue->pop_front();
            }
        } else {
            assert(false);
        }
    }
    cycle++;
}