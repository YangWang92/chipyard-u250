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

  bit __mc_req_valid;
  int __mc_req_rtnctl;
  longint __mc_req_data;
  longint __mc_req_addr;
  int __mc_req_size;
  int __mc_req_cmd;
  int __mc_req_scmd;
  bit __mc_req_stall;

  bit __mc_res_valid;
  int __mc_res_cmd;
  int __mc_res_scmd;
  longint __mc_res_data;
  int __mc_res_rtnctl;
  bit __mc_res_stall;

  bit __mc_req_flush;
  bit __mc_res_flush_ok;


  bit __disp_inst_valid;
  int __disp_inst_data;
  int __disp_reg_id;
  bit __disp_reg_read;
  bit __disp_reg_write;
  longint __disp_reg_wr_data;

  int __disp_aeg_cnt;
  int __disp_exception;
  bit __disp_idle;
  bit __disp_rtn_valid;
  longint __disp_rtn_data;
  bit __disp_stall;

  bit __csr_wr_valid;
  bit __csr_rd_valid;
  int __csr_addr;
  longint __csr_wr_data;

  bit __csr_rd_ack;
  longint __csr_rd_data;

  int __ae_id;

  bit __exit;

  reg        reg_exit;

  reg         reg_disp_inst_valid;
  reg  [4:0]  reg_disp_inst_data;
  reg  [17:0] reg_disp_reg_id;
  reg         reg_disp_reg_read;
  reg         reg_disp_reg_write;
  reg  [63:0] reg_disp_reg_wr_data;

  reg         reg_mc_req_stall;
  reg         reg_mc_res_valid;
  reg  [2:0]  reg_mc_res_cmd;
  reg  [3:0]  reg_mc_res_scmd;
  reg  [63:0] reg_mc_res_data;
  reg  [31:0] reg_mc_res_rtnctl;

  reg         reg_mc_res_flush_ok;
  reg         reg_csr_wr_valid;
  reg         reg_csr_rd_valid;
  reg  [15:0] reg_csr_addr;
  reg  [63:0] reg_csr_wr_data;

  reg  [3:0]  reg_ae_id;

  reg         in_reg_mc_req_valid;
  reg [31:0]  in_reg_mc_req_rtnctl;
  reg [63:0]  in_reg_mc_req_data;
  reg [47:0]  in_reg_mc_req_addr;
  reg [1:0]   in_reg_mc_req_size;
  reg [2:0]   in_reg_mc_req_cmd;
  reg [3:0]   in_reg_mc_req_scmd;
  reg         in_reg_mc_res_stall;
  reg         in_reg_mc_req_flush;
  reg         in_reg_disp_aeg_cnt;
  reg         in_reg_disp_exception;
  reg         in_reg_disp_idle;
  reg         in_reg_disp_rtn_valid;
  reg [63:0]  in_reg_disp_rtn_data;
  reg         in_reg_disp_stall;
  reg         in_reg_csr_rd_ack;
  reg [63:0]  in_reg_csr_rd_data;

  //doi function calls on negedge to avoid weird scheduling order stuff
  always @(negedge clock) begin
    if (reset) begin
    end else begin
      if (!initialized) begin
        initialized = 1'b1;
        wolverine_init();
      end
      wolverine_tick(
        __disp_inst_valid,
        __disp_inst_data,
        __disp_reg_id,
        __disp_reg_read,
        __disp_reg_write,
        __disp_reg_wr_data,

        __disp_aeg_cnt,
        __disp_exception,
        __disp_idle,
        __disp_rtn_valid,
        __disp_rtn_data,
        __disp_stall,

        __csr_wr_valid,
        __csr_rd_valid,
        __csr_addr,
        __csr_wr_data,

        __csr_rd_ack,
        __csr_rd_data,

        __ae_id,
        __exit
      );
      wolverine_mem_tick(
        __mc_req_valid,
        __mc_req_rtnctl,
        __mc_req_data,
        __mc_req_addr,
        __mc_req_size,
        __mc_req_cmd,
        __mc_req_scmd,
        __mc_req_stall,

        __mc_res_valid,
        __mc_res_cmd,
        __mc_res_scmd,
        __mc_res_data,
        __mc_res_rtnctl,
        __mc_res_stall,

        __mc_req_flush,
        __mc_res_flush_ok
      );
    end
  end


  always @(posedge clock) begin
    if (reset) begin
        __exit = 0;
        __disp_inst_valid = 0;
        __disp_inst_data = 0;
        __disp_reg_id = 0;
        __disp_reg_read = 0;
        __disp_reg_write = 0;
        __disp_reg_wr_data = 0;
        __mc_req_stall = 0;
        __mc_res_valid = 0;
        __mc_res_cmd = 0;
        __mc_res_scmd = 0;
        __mc_res_data = 0;
        __mc_res_rtnctl = 0;
        __mc_res_flush_ok = 0;
        __csr_wr_valid = 0;
        __csr_rd_valid = 0;
        __csr_addr = 0;
        __csr_wr_data = 0;
        __ae_id = 0;

        reg_exit <= 0;
        reg_disp_inst_valid <= 0;
        reg_disp_inst_data <= 0;
        reg_disp_reg_id <= 0;
        reg_disp_reg_read <= 0;
        reg_disp_reg_write <= 0;
        reg_disp_reg_wr_data <= 0;
        reg_mc_req_stall <= 0;
        reg_mc_res_valid <= 0;
        reg_mc_res_cmd <= 0;
        reg_mc_res_scmd <= 0;
        reg_mc_res_data <= 0;
        reg_mc_res_rtnctl <= 0;
        reg_mc_res_flush_ok <= 0;
        reg_csr_wr_valid <= 0;
        reg_csr_rd_valid <= 0;
        reg_csr_addr <= 0;
        reg_csr_wr_data <= 0;
        reg_ae_id <= 0;

        in_reg_mc_req_valid <= 0;
        in_reg_mc_req_rtnctl <= 0;
        in_reg_mc_req_data <= 0;
        in_reg_mc_req_addr <= 0;
        in_reg_mc_req_size <= 0;
        in_reg_mc_req_cmd <= 0;
        in_reg_mc_req_scmd <= 0;
        in_reg_mc_res_stall <= 0;
        in_reg_mc_req_flush <= 0;
        in_reg_disp_aeg_cnt <= 0;
        in_reg_disp_exception <= 0;
        in_reg_disp_idle <= 0;
        in_reg_disp_rtn_valid <= 0;
        in_reg_disp_rtn_data <= 0;
        in_reg_disp_stall <= 0;
        in_reg_csr_rd_ack <= 0;
        in_reg_csr_rd_data <= 0;

    end else begin
      reg_exit <= __exit;
      reg_disp_inst_valid <= __disp_inst_valid;
      reg_disp_inst_data <= __disp_inst_data;
      reg_disp_reg_id <= __disp_reg_id;
      reg_disp_reg_read <= __disp_reg_read;
      reg_disp_reg_write <= __disp_reg_write;
      reg_disp_reg_wr_data <= __disp_reg_wr_data;
      reg_mc_req_stall <= __mc_req_stall;
      reg_mc_res_valid <= __mc_res_valid;
      reg_mc_res_cmd <= __mc_res_cmd;
      reg_mc_res_scmd <= __mc_res_scmd;
      reg_mc_res_data <= __mc_res_data;
      reg_mc_res_rtnctl <= __mc_res_rtnctl;
      reg_mc_res_flush_ok <= __mc_res_flush_ok;
      reg_csr_wr_valid <= __csr_wr_valid;
      reg_csr_rd_valid <= __csr_rd_valid;
      reg_csr_addr <= __csr_addr;
      reg_csr_wr_data <= __csr_wr_data;
      reg_ae_id <= __ae_id;

      in_reg_mc_req_valid <= wolv_mcReqValid;
      in_reg_mc_req_rtnctl <= wolv_mcReqRtnCtl;
      in_reg_mc_req_data <= wolv_mcReqData;
      in_reg_mc_req_addr <= wolv_mcReqAddr;
      in_reg_mc_req_size <= wolv_mcReqSize;
      in_reg_mc_req_cmd <= wolv_mcReqCmd;
      in_reg_mc_req_scmd <= wolv_mcReqSCmd;
      in_reg_mc_res_stall <= wolv_mcResStall;
      in_reg_mc_req_flush <= wolv_mcReqFlush;
      in_reg_disp_aeg_cnt <= wolv_dispAegCnt;
      in_reg_disp_exception <= wolv_dispException;
      in_reg_disp_idle <= wolv_dispIdle;
      in_reg_disp_rtn_valid <= wolv_dispRtnValid;
      in_reg_disp_rtn_data <= wolv_dispRtnData;
      in_reg_disp_stall <= wolv_dispStall;
      in_reg_csr_rd_ack <= wolv_csrReadAck;
      in_reg_csr_rd_data <= wolv_csrReadData;
    end
  end

  /* verilator lint_off WIDTH */
  assign exit = reg_exit;

  assign __mc_req_valid = in_reg_mc_req_valid;
  assign __mc_req_rtnctl = in_reg_mc_req_rtnctl;
  assign __mc_req_data = in_reg_mc_req_data;
  assign __mc_req_addr = in_reg_mc_req_addr;
  assign __mc_req_size = in_reg_mc_req_size;
  assign __mc_req_cmd = in_reg_mc_req_cmd;
  assign __mc_req_scmd = in_reg_mc_req_scmd;
  assign wolv_mcReqStall = reg_mc_req_stall;

  assign wolv_mcResValid = reg_mc_res_valid;
  assign wolv_mcResCmd = reg_mc_res_cmd;
  assign wolv_mcResSCmd = reg_mc_res_scmd;
  assign wolv_mcResData = reg_mc_res_data;
  assign wolv_mcResRtnCtl = reg_mc_res_rtnctl;
  assign __mc_res_stall = in_reg_mc_res_stall;

  assign __mc_req_flush = in_reg_mc_req_flush;
  assign wolv_mcResFlushOK = reg_mc_res_flush_ok;


  assign wolv_dispInstValid = reg_disp_inst_valid;
  assign wolv_dispInstData = reg_disp_inst_data;
  assign wolv_dispRegID = reg_disp_reg_id;
  assign wolv_dispRegRead = reg_disp_reg_read;
  assign wolv_dispRegWrite = reg_disp_reg_write;
  assign wolv_dispRegWrData = reg_disp_reg_wr_data;

  assign __disp_aeg_cnt = in_reg_disp_aeg_cnt;
  assign __disp_exception = in_reg_disp_exception;
  assign __disp_idle = in_reg_disp_idle;
  assign __disp_rtn_valid = in_reg_disp_rtn_valid;
  assign __disp_rtn_data = in_reg_disp_rtn_data;
  assign __disp_stall = in_reg_disp_stall;

  assign wolv_csrWrValid = reg_csr_wr_valid;
  assign wolv_csrRdValid = reg_csr_rd_valid;
  assign wolv_csrAddr = reg_csr_addr;
  assign wolv_csrWrData = reg_csr_wr_data;

  assign __csr_rd_ack = in_reg_csr_rd_ack;
  assign __csr_rd_data = in_reg_csr_rd_data;

  assign wolv_aeid = reg_ae_id;

endmodule