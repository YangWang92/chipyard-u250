#include "wolverine_sim_driver.h"

#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>
#include <assert.h>
#include <stdio.h>
#include <sys/stat.h>

#define WOLVERINE_BASE_PADDR 0x43C00000L

#define TSI_OUT_FIFO_DATA 0x00
#define TSI_OUT_FIFO_COUNT 0x04
#define TSI_IN_FIFO_DATA 0x08
#define TSI_IN_FIFO_COUNT 0x0C
#define SYSTEM_RESET 0x10
#define BLKDEV_REQ_FIFO_DATA 0x20
#define BLKDEV_REQ_FIFO_COUNT 0x24
#define BLKDEV_DATA_FIFO_DATA 0x28
#define BLKDEV_DATA_FIFO_COUNT 0x2C
#define BLKDEV_RESP_FIFO_DATA 0x30
#define BLKDEV_RESP_FIFO_COUNT 0x34
#define BLKDEV_NSECTORS 0x38
#define BLKDEV_MAX_REQUEST_LENGTH 0x3C
#define NET_OUT_FIFO_DATA 0x40
#define NET_OUT_FIFO_COUNT 0x44
#define NET_IN_FIFO_DATA 0x48
#define NET_IN_FIFO_COUNT 0x4C
#define NET_MACADDR_LO 0x50
#define NET_MACADDR_HI 0x54

#define BLKDEV_REQ_NWORDS 3
#define BLKDEV_DATA_NWORDS 3
#define NET_FLIT_NWORDS 3

#define CSR_ADAPT_AXI 8


wolverine_driver_t::wolverine_driver_t(tsi_t *tsi, BlockDevice *bdev, wolverine_interface *wi)
{
    this->tsi = tsi;
    this->bdev = bdev;
    this->wi = wi;

    printf("Waiting for marked as stalled...");
    while(readCSR(0)!=1);
    printf("...done\n");
    // address was written
    uint64_t badr_csr_value = readCSR(1);
    printf("badr_csr_value: %p\n", (void *)badr_csr_value);

    // reset the target
    write(SYSTEM_RESET, 1);
    write(SYSTEM_RESET, 0);

    // set nsectors and max_request_length
    if (bdev == NULL) {
        write(BLKDEV_NSECTORS, 0);
        write(BLKDEV_MAX_REQUEST_LENGTH, 0);
    } else {
        write(BLKDEV_NSECTORS, bdev->nsectors());
        write(BLKDEV_MAX_REQUEST_LENGTH, bdev->max_request_length());
    }
}

wolverine_driver_t::~wolverine_driver_t()
{
    // make dispatch finish
    writeCSR(0, 0);
    assert(readCSR(0)==0);
}
  void wolverine_driver_t::writeCSR(unsigned int regInd, uint64_t regValue) {
    wi->writeCSR(regInd, regValue);
  }

  uint64_t wolverine_driver_t::readCSR(unsigned int regInd) {
    uint64_t res = wi->readCSR(regInd);
    return res;
  }

uint32_t wolverine_driver_t::read(int off)
{
    uint64_t tmp = (((uint64_t)(0x00000000UL | WOLVERINE_BASE_PADDR | off)) << 32);
    writeCSR(CSR_ADAPT_AXI, tmp);
    uint64_t result = readCSR(CSR_ADAPT_AXI);
//    printf("R %x - %x\n", off, (uint32_t)result);
    return result;
}

void wolverine_driver_t::write(int off, uint32_t word)
{
//    printf("W %x - %x\n", off, word);
    uint64_t tmp = (((uint64_t)(0x80000000UL | WOLVERINE_BASE_PADDR | off)) << 32) | word;
    writeCSR(CSR_ADAPT_AXI, tmp);
}

struct blkdev_request wolverine_driver_t::read_blkdev_request()
{
    uint32_t word;
    struct blkdev_request req;

    // tag + write
    word = read(BLKDEV_REQ_FIFO_DATA);
    req.write = word & 0x1;
    req.tag = word >> 1;
    // offset, then len
    req.offset = read(BLKDEV_REQ_FIFO_DATA);
    req.len = read(BLKDEV_REQ_FIFO_DATA);

    return req;
}

struct blkdev_data wolverine_driver_t::read_blkdev_req_data()
{
    struct blkdev_data data;

    data.tag = read(BLKDEV_DATA_FIFO_DATA);
    data.data = read(BLKDEV_DATA_FIFO_DATA) & 0xffffffffU;
    data.data |= ((uint64_t) read(BLKDEV_DATA_FIFO_DATA)) << 32;

    return data;
}

void wolverine_driver_t::write_blkdev_response(struct blkdev_data &resp)
{
    write(BLKDEV_RESP_FIFO_DATA, resp.tag);
    write(BLKDEV_RESP_FIFO_DATA, resp.data & 0xffffffffU);
    write(BLKDEV_RESP_FIFO_DATA, resp.data >> 32);
}

void wolverine_driver_t::poll(void)
{
    if (tsi != NULL) {
        // don't have to check count every time => bandwidth almost doubled
        // TODO: use modify instead of write, read for consecutive reads?
        int cnt;
        while ((cnt=read(TSI_OUT_FIFO_COUNT)) > 0) {
            for(int i=0; i<cnt; i++){
                uint32_t out_data = read(TSI_OUT_FIFO_DATA);
                tsi->send_word(out_data);
            }
        }

        while (tsi->data_available() && (cnt=read(TSI_IN_FIFO_COUNT)) > 0) {
            for(int i=0; i<cnt && tsi->data_available(); i++){
                uint32_t in_data = tsi->recv_word();
                write(TSI_IN_FIFO_DATA, in_data);
            }
        }

        tsi->switch_to_host();
    }

    if (bdev != NULL) {
        while (read(BLKDEV_REQ_FIFO_COUNT) >= BLKDEV_REQ_NWORDS) {
            struct blkdev_request req = read_blkdev_request();
            bdev->send_request(req);
        }

        while (read(BLKDEV_DATA_FIFO_COUNT) >= BLKDEV_DATA_NWORDS) {
            struct blkdev_data data = read_blkdev_req_data();
            bdev->send_data(data);
        }

        while (bdev->resp_valid() && read(BLKDEV_RESP_FIFO_COUNT) >= BLKDEV_DATA_NWORDS) {
            struct blkdev_data resp = bdev->recv_response();
            write_blkdev_response(resp);
        }

        bdev->switch_to_host();
    }
}
