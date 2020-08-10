import "DPI-C" function void wolverine_mem_tick
(
   input bit mc_req_valid,
   input int mc_req_rtnctl,
   input longint mc_req_data,
   input longint mc_req_addr,
   input int mc_req_size,
   input int mc_req_cmd,
   input int mc_req_scmd,
   output bit mc_req_stall,

   output bit mc_res_valid,
   output int mc_res_cmd,
   output int mc_res_scmd,
   output longint mc_res_data,
   output int mc_res_rtnctl,
   input bit mc_res_stall,

   input bit mc_req_flush,
   output bit mc_res_flush_ok
);

import "DPI-C" function void wolverine_init
  (
  );

import "DPI-C" function void wolverine_tick(
    output bit disp_inst_valid,
    output int disp_inst_data,
    output int disp_reg_id,
    output bit disp_reg_read,
    output bit disp_reg_write,
    output longint disp_reg_wr_data,

    input int disp_aeg_cnt,
    input int disp_exception,
    input bit disp_idle,
    input bit disp_rtn_valid,
    input longint disp_rtn_data,
    input bit disp_stall,

    output bit csr_wr_valid,
    output bit csr_rd_valid,
    output int csr_addr,
    output longint csr_wr_data,

    input bit csr_rd_ack,
    input longint csr_rd_data,

    output int ae_id,

    output bit exit
);

module SimWolverineTestHarness(
  input         clock, 
  input         reset,
  output        exit,
  output         wolv_dispInstValid,
  output  [4:0]  wolv_dispInstData,
  output  [17:0] wolv_dispRegID,
  output         wolv_dispRegRead,
  output         wolv_dispRegWrite,
  output  [63:0] wolv_dispRegWrData,
  input   [17:0] wolv_dispAegCnt,
  input   [15:0] wolv_dispException,
  input        wolv_dispIdle,
  input        wolv_dispRtnValid,
  input [63:0] wolv_dispRtnData,
  input        wolv_dispStall,
  input        wolv_mcReqValid,
  input [31:0] wolv_mcReqRtnCtl,
  input [63:0] wolv_mcReqData,
  input [47:0] wolv_mcReqAddr,
  input [1:0]  wolv_mcReqSize,
  input [2:0]  wolv_mcReqCmd,
  input [3:0]  wolv_mcReqSCmd,
  output         wolv_mcReqStall,
  output         wolv_mcResValid,
  output  [2:0]  wolv_mcResCmd,
  output  [3:0]  wolv_mcResSCmd,
  output  [63:0] wolv_mcResData,
  output  [31:0] wolv_mcResRtnCtl,
  input        wolv_mcResStall,
  input        wolv_mcReqFlush,
  output         wolv_mcResFlushOK,
  output         wolv_csrWrValid,
  output         wolv_csrRdValid,
  output  [15:0] wolv_csrAddr,
  output  [63:0] wolv_csrWrData,
  input        wolv_csrReadAck,
  input [63:0] wolv_csrReadData,
  output  [3:0]  wolv_aeid
);
  reg initialized = 1'b0;

  wire bit mc_req_valid;
  wire int mc_req_rtnctl;
  wire longint mc_req_data;
  wire longint mc_req_addr;
  wire int mc_req_size;
  wire int mc_req_cmd;
  wire int mc_req_scmd;
  wire bit mc_req_stall;

  wire bit mc_res_valid;
  wire int mc_res_cmd;
  wire int mc_res_scmd;
  wire longint mc_res_data;
  wire int mc_res_rtnctl;
  wire bit mc_res_stall;

  wire bit mc_req_flush;
  wire bit mc_res_flush_ok;


  wire bit disp_inst_valid;
  wire int disp_inst_data;
  wire int disp_reg_id;
  wire bit disp_reg_read;
  wire bit disp_reg_write;
  wire longint disp_reg_wr_data;

  wire int disp_aeg_cnt;
  wire int disp_exception;
  wire bit disp_idle;
  wire bit disp_rtn_valid;
  wire longint disp_rtn_data;
  wire bit disp_stall;

  wire bit csr_wr_valid;
  wire bit csr_rd_valid;
  wire int csr_addr;
  wire longint csr_wr_data;

  wire bit csr_rd_ack;
  wire longint csr_rd_data;

  wire int ae_id;

  wire bit w_exit;

  reg exit_reg = 1'b0;

  always @(posedge clock) begin
    if (reset) begin
      //wolv_dispInstValid = 1'b0;
      //wolv_dispRegWrite = 1'b0;
      //wolv_dispRegRead = 1'b0;
      //wolv_mcReqStall = 1'b0;
      //wolv_mcResValid = 1'b0;
      //wolv_csrWrValid = 1'b0;
      //wolv_csrRdValid = 1'b0;
      exit_reg <= 1'b0;
    end else begin
      if (!initialized) begin
        initialized = 1'b1;
        wolverine_init();
      end
      wolverine_tick(
        disp_inst_valid,
        disp_inst_data,
        disp_reg_id,
        disp_reg_read,
        disp_reg_write,
        disp_reg_wr_data,

        disp_aeg_cnt,
        disp_exception,
        disp_idle,
        disp_rtn_valid,
        disp_rtn_data,
        disp_stall,

        csr_wr_valid,
        csr_rd_valid,
        csr_addr,
        csr_wr_data,

        csr_rd_ack,
        csr_rd_data,

        ae_id,
        w_exit
      );
      wolverine_mem_tick(
        mc_req_valid,
        mc_req_rtnctl,
        mc_req_data,
        mc_req_addr,
        mc_req_size,
        mc_req_cmd,
        mc_req_scmd,
        mc_req_stall,

        mc_res_valid,
        mc_res_cmd,
        mc_res_scmd,
        mc_res_data,
        mc_res_rtnctl,
        mc_res_stall,

        mc_req_flush,
        mc_res_flush_ok
      );
      exit_reg <= w_exit;
    end
  end

  /* verilator lint_off WIDTH */
  assign mc_req_valid = wolv_mcReqValid;
  assign mc_req_rtnctl = wolv_mcReqRtnCtl;
  assign mc_req_data = wolv_mcReqData;
  assign mc_req_addr = wolv_mcReqAddr;
  assign mc_req_size = wolv_mcReqSize;
  assign mc_req_cmd = wolv_mcReqCmd;
  assign mc_req_scmd = wolv_mcReqSCmd;
  assign wolv_mcReqStall = mc_req_stall;

  assign wolv_mcResValid = mc_res_valid;
  assign wolv_mcResCmd = mc_res_cmd;
  assign wolv_mcResSCmd = mc_res_scmd;
  assign wolv_mcResData = mc_res_data;
  assign wolv_mcResRtnCtl = mc_res_rtnctl;
  assign mc_res_stall = wolv_mcResStall;

  assign mc_req_flush = wolv_mcReqFlush;
  assign wolv_mcResFlushOK = mc_res_flush_ok;


  assign wolv_dispInstValid = disp_inst_valid;
  assign wolv_dispInstData = disp_inst_data;
  assign wolv_dispRegID = disp_reg_id;
  assign wolv_dispRegRead = disp_reg_read;
  assign wolv_dispRegWrite = disp_reg_write;
  assign wolv_dispRegWrData = disp_reg_wr_data;

  assign disp_aeg_cnt = wolv_dispAegCnt;
  assign disp_exception = wolv_dispException;
  assign disp_idle = wolv_dispIdle;
  assign disp_rtn_valid = wolv_dispRtnValid;
  assign disp_rtn_data = wolv_dispRtnData;
  assign disp_stall = wolv_dispStall;

  assign wolv_csrWrValid = csr_wr_valid;
  assign wolv_csrRdValid = csr_rd_valid;
  assign wolv_csrAddr = csr_addr;
  assign wolv_csrWrData = csr_wr_data;

  assign csr_rd_ack = wolv_csrReadAck;
  assign csr_rd_data = wolv_csrReadData;

  assign wolv_aeid = ae_id;

  assign exit = exit_reg;

endmodule