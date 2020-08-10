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
    *mc_res_valid = 0;

}