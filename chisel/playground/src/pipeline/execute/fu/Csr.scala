package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class Csr extends Module {
  val io = IO(new Bundle {
    val info     = Input(new Info())
    val src_info = Input(new SrcInfo())
    val result   = Output(UInt(XLEN.W))
  })

  object CsrAddr {
    // Unprivileged CSRs
    val CYCLE      = "hC00".U(12.W)
    
    // M-mode CSRs (Machine Information Registers)
    val MVENDORID  = "hF11".U(12.W)
    val MARCHID    = "hF12".U(12.W)
    val MIMPID     = "hF13".U(12.W)
    val MHARTID    = "hF15".U(12.W)
    
    // M-mode CSRs (Machine Trap Setup)
    val MSTATUS    = "h300".U(12.W)
    val MISA       = "h301".U(12.W)
    val MIE        = "h304".U(12.W)
    val MTVEC      = "h305".U(12.W)
    val MCOUNTEREN = "h306".U(12.W)
    
    // M-mode CSRs (Machine Trap Handling)
    val MSCRATCH   = "h340".U(12.W)
    val MEPC       = "h341".U(12.W)
    val MCAUSE     = "h342".U(12.W)
    val MTVAL      = "h343".U(12.W)
    val MIP        = "h344".U(12.W)
  }
  
  val addr = io.info.inst(31, 20)
  
  val csri = ZeroExtend(io.info.inst(19, 15), XLEN)
  
  val src1_data = io.src_info.src1_data
  
  class CsrReg(initVal: UInt, readMask: UInt, writeMask: UInt) {
    val reg = RegInit(initVal)
    def read(): UInt = reg & readMask
    def write(data: UInt): Unit = {
      when(writeMask =/= 0.U) {
        reg := (reg & (~writeMask)) | (data & writeMask)
      }
    }
  }
  
  val fullMask = Fill(XLEN, 1.U(1.W))
  val zeroMask = 0.U(XLEN.W)
  
  val csrRegs = Map(
    // Unprivileged CSRs
    CsrAddr.CYCLE      -> new CsrReg(0.U(XLEN.W), fullMask, zeroMask),                          // URO
    
    // M-mode CSRs
    CsrAddr.MVENDORID  -> new CsrReg(0.U(XLEN.W), fullMask, zeroMask),                          // MRO
    CsrAddr.MARCHID    -> new CsrReg(0.U(XLEN.W), fullMask, zeroMask),                          // MRO
    CsrAddr.MIMPID     -> new CsrReg(0.U(XLEN.W), fullMask, zeroMask),                          // MRO
    CsrAddr.MHARTID    -> new CsrReg(0.U(XLEN.W), fullMask, zeroMask),                          // MRO
    CsrAddr.MSTATUS    -> new CsrReg("h0000000000001800".U(XLEN.W), fullMask, "h88".U(XLEN.W)), // MRW
    CsrAddr.MISA       -> new CsrReg("h8000000000001100".U(XLEN.W), fullMask, zeroMask),        // MRW (RV64I + M extension)
    CsrAddr.MIE        -> new CsrReg(0.U(XLEN.W), fullMask, fullMask),                          // MRW
    CsrAddr.MTVEC      -> new CsrReg(0.U(XLEN.W), fullMask, fullMask),                          // MRW
    CsrAddr.MCOUNTEREN -> new CsrReg(0.U(XLEN.W), fullMask, fullMask),                          // MRW
    CsrAddr.MSCRATCH   -> new CsrReg(0.U(XLEN.W), fullMask, fullMask),                          // MRW
    CsrAddr.MEPC       -> new CsrReg(0.U(XLEN.W), fullMask, fullMask),                          // MRW
    CsrAddr.MCAUSE     -> new CsrReg(0.U(XLEN.W), fullMask, fullMask),                          // MRW
    CsrAddr.MTVAL      -> new CsrReg(0.U(XLEN.W), fullMask, fullMask),                          // MRW
    CsrAddr.MIP        -> new CsrReg(0.U(XLEN.W), fullMask, "h888".U(XLEN.W))                   // MRW
  )
  
  csrRegs(CsrAddr.CYCLE).reg := csrRegs(CsrAddr.CYCLE).reg + 1.U

  val rdata = WireInit(0.U(XLEN.W))
  val addrHitRead = csrRegs.map { case (csrAddr, _) => addr === csrAddr }.foldLeft(false.B)(_ || _)
  val validCsrAddrRead = addrHitRead

  when(validCsrAddrRead) {
    rdata := MuxLookup(addr, 0.U(XLEN.W))(
      csrRegs.map { case (csrAddr, csrReg) => csrAddr -> csrReg.read() }.toSeq
    )
  } .otherwise {
    rdata := 0.U(XLEN.W)
  }

  val write_enable = WireInit(false.B)
  val csr_write_data = Wire(UInt(XLEN.W))

  val opType = io.info.op(2, 0)

  csr_write_data := MuxCase(0.U, Seq(
    (opType === CSROpType.write)  -> src1_data,
    (opType === CSROpType.set)    -> (rdata | src1_data),
    (opType === CSROpType.clear)  -> (rdata & (~src1_data)),
    (opType === CSROpType.writei) -> csri,
    (opType === CSROpType.seti)   -> (rdata | csri),
    (opType === CSROpType.cleari) -> (rdata & (~csri))
  ))

  when(io.info.valid && io.info.fusel === FuType.csr) {
    val is_rs1_zero = src1_data === 0.U
    val is_imm_zero = csri(4,0) === 0.U

    write_enable := MuxLookup(opType, false.B)(Seq(
      CSROpType.write  -> true.B,
      CSROpType.set    -> !is_rs1_zero,
      CSROpType.clear  -> !is_rs1_zero,
      CSROpType.writei -> true.B,
      CSROpType.seti   -> !is_imm_zero,
      CSROpType.cleari -> !is_imm_zero
    ))
  }

  when(validCsrAddrRead && write_enable) {
    for ((csrAddr, csrReg) <- csrRegs) {
      when(addr === csrAddr) {
        csrReg.write(csr_write_data)
      }
    }
  }

  io.result := rdata
}