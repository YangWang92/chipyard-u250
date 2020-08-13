#ifndef __WOLVERINE_DRIVER_H
#define __WOLVERINE_DRIVER_H

#include "fesvr/tsi.h"
#include "blkdev.h"
#include <stdint.h>
#include "SimWolverineTestHarness.h"

class wolverine_driver_t {
  public:
    wolverine_driver_t(tsi_t *tsi, BlockDevice *bdev, wolverine_interface *wi);
    ~wolverine_driver_t();

    void poll(void);

  private:
    tsi_t *tsi;
    BlockDevice *bdev;
    wolverine_interface *wi;

  protected:
    uint32_t read(int off);
    void write(int off, uint32_t word);
    struct blkdev_request read_blkdev_request();
    struct blkdev_data read_blkdev_req_data();
    void write_blkdev_response(struct blkdev_data &resp);
    void writeCSR(unsigned int regInd, uint64_t regValue);
    uint64_t readCSR(unsigned int regInd);
};

#endif
