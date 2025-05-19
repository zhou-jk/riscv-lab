package cpu.cache

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.defines.AXIParam._

// AXI转接桥，用于连接ICache和DCache并处理读请求的优先级
class CacheAXIInterface extends Module {
  val io = IO(new Bundle {
    val icache = Flipped(new AXIMaster())
    val dcache = Flipped(new AXIMaster())
    val axi    = new AXIMaster()
  })
  
  // 互斥锁状态寄存器
  val ar_sel_lock = RegInit(false.B)
  val ar_sel_val = RegInit(false.B)
  
  // 当读通道锁定时，记录选择的是哪个Cache
  // 当ar_sel_val为true时，选择DCache的请求
  // 当ar_sel_val为false时，选择ICache的请求
  
  // 判断是否有读请求
  val icache_ar_valid = io.icache.arvalid
  val dcache_ar_valid = io.dcache.arvalid
  
  // 读通道仲裁逻辑（优先响应DCache的读请求）
  when (!ar_sel_lock) {
    when (dcache_ar_valid) {
      ar_sel_lock := true.B
      ar_sel_val := true.B  // 选择DCache
    }.elsewhen (icache_ar_valid) {
      ar_sel_lock := true.B
      ar_sel_val := false.B // 选择ICache
    }
  }.otherwise {
    // 当当前选中的Cache完成了握手，释放锁
    when ((ar_sel_val && io.axi.arready && io.axi.arvalid) || 
          (!ar_sel_val && io.axi.arready && io.axi.arvalid)) {
      ar_sel_lock := false.B
    }
  }
  
  // 读请求通道选择
  io.axi.arid    := Mux(ar_sel_val, io.dcache.arid, io.icache.arid)
  io.axi.araddr  := Mux(ar_sel_val, io.dcache.araddr, io.icache.araddr)
  io.axi.arlen   := Mux(ar_sel_val, io.dcache.arlen, io.icache.arlen)
  io.axi.arsize  := Mux(ar_sel_val, io.dcache.arsize, io.icache.arsize)
  io.axi.arburst := Mux(ar_sel_val, io.dcache.arburst, io.icache.arburst)
  io.axi.arlock  := Mux(ar_sel_val, io.dcache.arlock, io.icache.arlock)
  io.axi.arcache := Mux(ar_sel_val, io.dcache.arcache, io.icache.arcache)
  io.axi.arprot  := Mux(ar_sel_val, io.dcache.arprot, io.icache.arprot)
  io.axi.arvalid := Mux(ar_sel_val, io.dcache.arvalid, io.icache.arvalid)
  
  // 读响应通道分发 - 根据返回的rid分发到对应的Cache
  io.icache.rdata  := io.axi.rdata
  io.icache.rid    := io.axi.rid
  io.icache.rresp  := io.axi.rresp
  io.icache.rlast  := io.axi.rlast
  io.icache.rvalid := io.axi.rvalid && (io.axi.rid === AXI_ID_ICACHE)
  
  io.dcache.rdata  := io.axi.rdata
  io.dcache.rid    := io.axi.rid
  io.dcache.rresp  := io.axi.rresp
  io.dcache.rlast  := io.axi.rlast
  io.dcache.rvalid := io.axi.rvalid && (io.axi.rid === AXI_ID_DCACHE)
  
  // 读响应就绪信号的或操作
  io.axi.rready := Mux(io.axi.rid === AXI_ID_ICACHE, io.icache.rready, io.dcache.rready)
  
  // arready信号的分发
  io.icache.arready := io.axi.arready && !ar_sel_val && ar_sel_lock
  io.dcache.arready := io.axi.arready && ar_sel_val && ar_sel_lock
  
  // 写请求通道直接连接DCache（ICache不需要写入）
  io.axi.awid    := io.dcache.awid
  io.axi.awaddr  := io.dcache.awaddr
  io.axi.awlen   := io.dcache.awlen
  io.axi.awsize  := io.dcache.awsize
  io.axi.awburst := io.dcache.awburst
  io.axi.awlock  := io.dcache.awlock
  io.axi.awcache := io.dcache.awcache
  io.axi.awprot  := io.dcache.awprot
  io.axi.awvalid := io.dcache.awvalid
  
  io.dcache.awready := io.axi.awready
  io.icache.awready := false.B // ICache不需要写入
  
  // 写数据通道直接连接DCache
  io.axi.wid    := io.dcache.wid
  io.axi.wdata  := io.dcache.wdata
  io.axi.wstrb  := io.dcache.wstrb
  io.axi.wlast  := io.dcache.wlast
  io.axi.wvalid := io.dcache.wvalid
  
  io.dcache.wready := io.axi.wready
  io.icache.wready := false.B // ICache不需要写入
  
  // 写响应通道直接连接DCache
  io.dcache.bid    := io.axi.bid
  io.dcache.bresp  := io.axi.bresp
  io.dcache.bvalid := io.axi.bvalid
  
  io.icache.bid    := 0.U // ICache不需要写入
  io.icache.bresp  := 0.U
  io.icache.bvalid := false.B
  
  io.axi.bready := io.dcache.bready
  
  // 全局时钟和复位信号连接 - 使用顶层传入的时钟和复位信号
  io.icache.aclock := io.axi.aclock
  io.icache.areset := io.axi.areset
  io.dcache.aclock := io.axi.aclock
  io.dcache.areset := io.axi.areset
} 