module top(
    input       clock,
    input       reset,
    // Interrupts
    input       mei, // to PLIC
    input       msi, // to CLINT
    input       mti, // to CLINT
    input       sei, // to PLIC
    // aw
    output [3:0]axi_awid,
    output[31:0]axi_awaddr,
    output [7:0]axi_awlen,
    output [2:0]axi_awsize,
    output [1:0]axi_awburst,
    output      axi_awvalid,
    input       axi_awready,
    // w
    output[63:0]axi_wdata,
    output [7:0]axi_wstrb,
    output      axi_wlast,
    output      axi_wvalid,
    input       axi_wready,
    // b
    input  [3:0]axi_bid,
    input  [1:0]axi_bresp,
    input       axi_bvalid,
    output      axi_bready,
    // ar
    output [3:0]axi_arid,
    output[31:0]axi_araddr,
    output [7:0]axi_arlen,
    output [2:0]axi_arsize,
    output [1:0]axi_arburst,
    output      axi_arvalid,
    input       axi_arready,
    // r
    input  [3:0]axi_rid,
    input [63:0]axi_rdata,
    input  [1:0]axi_rresp,
    input       axi_rlast,
    input       axi_rvalid,
    output      axi_rready,
    // debug
    output      debug_commit,
    output[63:0]debug_pc,
    output[4:0] debug_rf_wnum,
    output[63:0]debug_rf_wdata
);

// 定义默认值
wire [1:0]  axi_awlock = 2'b0;  // 非原子访问
wire [3:0]  axi_awcache = 4'b0;  // 非缓存访问
wire [2:0]  axi_awprot = 3'b0;   // 非特权访问
wire [3:0]  axi_wid = 4'h1;      // 写ID
wire [1:0]  axi_arlock = 2'b0;   // 非原子访问
wire [3:0]  axi_arcache = 4'b0;  // 非缓存访问
wire [2:0]  axi_arprot = 3'b0;   // 非特权访问

// 调试辅助变量
reg [15:0] cycle_count;
initial cycle_count = 0;

// 记录AXI握手信号计数
reg [31:0] ar_req_count = 0;
reg [31:0] r_data_count = 0;
reg [31:0] commit_count = 0;

always @(posedge clock) begin
    if (reset) begin
        cycle_count <= 0;
        ar_req_count <= 0;
        r_data_count <= 0;
        commit_count <= 0;
    end
    else begin
        cycle_count <= cycle_count + 1;
        
        // 计数AR请求和R响应
        if (axi_arvalid && axi_arready)
            ar_req_count <= ar_req_count + 1;
            
        if (axi_rvalid && axi_rready)
            r_data_count <= r_data_count + 1;
            
        if (debug_commit)
            commit_count <= commit_count + 1;
    end
    
    // 每10个周期输出状态 (仿真调试用)
    if (cycle_count < 50 || cycle_count % 10 == 0) begin
        // 打印AXI信号状态和统计数据
        $display("[Cycle %0d] PC=%h, AR: valid=%b ready=%b addr=%h, AR_CNT=%0d", 
                 cycle_count, debug_pc, axi_arvalid, axi_arready, axi_araddr, ar_req_count);
        $display("[Cycle %0d] R: valid=%b ready=%b data=%h, R_CNT=%0d, Commit=%0d", 
                 cycle_count, axi_rvalid, axi_rready, axi_rdata, r_data_count, commit_count);
    end
    
    // AXI握手监控
    if (axi_arvalid && axi_arready) begin
        $display("[Cycle %0d] AR-HANDSHAKE: araddr=%h, arid=%h", 
                 cycle_count, axi_araddr, axi_arid);
    end
    
    if (axi_rvalid && axi_rready) begin
        $display("[Cycle %0d] R-HANDSHAKE: rdata=%h, rid=%h, rlast=%b", 
                 cycle_count, axi_rdata, axi_rid, axi_rlast);
    end
    
    // 长时间无响应检测
    if (axi_arvalid && !axi_arready && cycle_count > 100 && cycle_count % 100 == 0) begin
        $display("[WARN] ARVALID asserted for %0d cycles without ARREADY", cycle_count);
    end
    
    if (axi_rvalid && !axi_rready && cycle_count > 100 && cycle_count % 100 == 0) begin
        $display("[WARN] RVALID asserted for %0d cycles without RREADY", cycle_count);
    end
end

PuaCpu core(
    .clock                    (clock),
    .reset                    (reset),
    // Interrupts     
    .io_ext_int_mei           (mei), // to PLIC
    .io_ext_int_msi           (msi), // to CLINT
    .io_ext_int_mti           (mti), // to CLINT
    .io_ext_int_sei           (sei), // to PLIC
    // 添加AXI时钟和复位信号
    .io_axi_aclock            (clock),
    .io_axi_areset            (reset),
    // aw 
    .io_axi_awid              (axi_awid),
    .io_axi_awaddr            (axi_awaddr),
    .io_axi_awlen             (axi_awlen),
    .io_axi_awsize            (axi_awsize),
    .io_axi_awburst           (axi_awburst),
    .io_axi_awvalid           (axi_awvalid),
    .io_axi_awready           (axi_awready),
    .io_axi_awlock            (axi_awlock),
    .io_axi_awcache           (axi_awcache),
    .io_axi_awprot            (axi_awprot),
    // w 
    .io_axi_wdata             (axi_wdata),
    .io_axi_wstrb             (axi_wstrb),
    .io_axi_wlast             (axi_wlast),
    .io_axi_wvalid            (axi_wvalid),
    .io_axi_wready            (axi_wready),
    .io_axi_wid               (axi_wid),
    // b 
    .io_axi_bid               (axi_bid),
    .io_axi_bresp             (axi_bresp),
    .io_axi_bvalid            (axi_bvalid),
    .io_axi_bready            (axi_bready),
    // ar 
    .io_axi_arid              (axi_arid),
    .io_axi_araddr            (axi_araddr),
    .io_axi_arlen             (axi_arlen),
    .io_axi_arsize            (axi_arsize),
    .io_axi_arburst           (axi_arburst),
    .io_axi_arvalid           (axi_arvalid),
    .io_axi_arready           (axi_arready),
    .io_axi_arlock            (axi_arlock),
    .io_axi_arcache           (axi_arcache),
    .io_axi_arprot            (axi_arprot),
    // r 
    .io_axi_rid               (axi_rid),
    .io_axi_rdata             (axi_rdata),
    .io_axi_rresp             (axi_rresp),
    .io_axi_rlast             (axi_rlast),
    .io_axi_rvalid            (axi_rvalid),
    .io_axi_rready            (axi_rready),
    // debug
    .io_debug_pc              (debug_pc),
    .io_debug_commit          (debug_commit),
    .io_debug_rf_wnum         (debug_rf_wnum),
    .io_debug_rf_wdata        (debug_rf_wdata)
);

endmodule