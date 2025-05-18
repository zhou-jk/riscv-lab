package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class Csr extends Module with HasExceptionNO {
  val io = IO(new Bundle {
    val info           = Input(new Info())
    val src_info       = Input(new SrcInfo())
    val pc             = Input(UInt(XLEN.W))
    val ex             = Input(new ExceptionInfo())
    val interrupt      = Input(new ExtInterrupt())
    val result         = Output(UInt(XLEN.W))
    val ex_out         = Output(new ExceptionInfo())
    val mode           = Output(UInt(2.W))
    val interrupt_out  = Output(Vec(INT_WID, Bool()))
    
    val has_exception  = Output(Bool())
    val flush          = Output(Bool())
    val target         = Output(UInt(XLEN.W))
    val reg_wen_out    = Output(Bool())
  })

  object CsrAddr {
    // Unprivileged CSRs
    val CYCLE      = "hC00".U(12.W)
    
    // M-mode CSRs (Machine Information Registers)
    val MVENDORID  = "hF11".U(12.W)
    val MARCHID    = "hF12".U(12.W)
    val MIMPID     = "hF13".U(12.W)
    val MHARTID    = "hF14".U(12.W)
    
    // M-mode CSRs (Machine Trap Setup)
    val MSTATUS    = "h300".U(12.W)
    val MISA       = "h301".U(12.W)
    // val MEDELEG    = "h302".U(12.W)
    // val MIDELEG    = "h303".U(12.W)
    val MIE        = "h304".U(12.W)
    val MTVEC      = "h305".U(12.W)
    val MCOUNTEREN = "h306".U(12.W)
    
    // M-mode CSRs (Machine Trap Handling)
    val MSCRATCH   = "h340".U(12.W)
    val MEPC       = "h341".U(12.W)
    val MCAUSE     = "h342".U(12.W)
    val MTVAL      = "h343".U(12.W)
    val MIP        = "h344".U(12.W)
    
    // M-mode CSRs (Debug/Trace Registers - Triggers)
    val TSELECT    = "h7A0".U(12.W)
    val TDATA1     = "h7A1".U(12.W)
    val TDATA2     = "h7A2".U(12.W)
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
  
  val mode = RegInit(Priv.m)
  io.mode := mode

  val csrRegs = Map(
    CsrAddr.CYCLE      -> new CsrReg(0.U(XLEN.W), fullMask, zeroMask), // URO
    
    // M-mode CSRs
    CsrAddr.MVENDORID  -> new CsrReg(0.U(XLEN.W), fullMask, zeroMask), // MRO
    CsrAddr.MARCHID    -> new CsrReg(0.U(XLEN.W), fullMask, zeroMask), // MRO
    CsrAddr.MIMPID     -> new CsrReg(0.U(XLEN.W), fullMask, zeroMask), // MRO
    CsrAddr.MHARTID    -> new CsrReg(0.U(XLEN.W), fullMask, zeroMask), // MRO
    CsrAddr.MSTATUS    -> new CsrReg("h00000002_00000000".U(XLEN.W), fullMask, "h00000000_00021888".U(XLEN.W)), // MRW - Initial value sets UXL to 64-bit and MPP to U-mode
    CsrAddr.MISA       -> new CsrReg("h80000000_00101100".U(XLEN.W), fullMask, zeroMask), // MRO (RV64I + M + U extension)
    // CsrAddr.MEDELEG    -> new CsrReg(0.U(XLEN.W), fullMask, fullMask), //MRW
    // CsrAddr.MIDELEG    -> new CsrReg(0.U(XLEN.W), fullMask, fullMask), //MRW
    CsrAddr.MIE        -> new CsrReg(0.U(XLEN.W), fullMask, fullMask), //MRW
    CsrAddr.MTVEC      -> new CsrReg(0.U(XLEN.W), fullMask, fullMask), //MRW
    CsrAddr.MCOUNTEREN -> new CsrReg(0.U(XLEN.W), fullMask, fullMask), //MRW
    CsrAddr.MSCRATCH   -> new CsrReg(0.U(XLEN.W), fullMask, fullMask), //MRW
    CsrAddr.MEPC       -> new CsrReg(0.U(XLEN.W), fullMask, fullMask), //MRW
    CsrAddr.MCAUSE     -> new CsrReg(0.U(XLEN.W), fullMask, fullMask), //MRW
    CsrAddr.MTVAL      -> new CsrReg(0.U(XLEN.W), fullMask, fullMask), //MRW
    CsrAddr.MIP        -> new CsrReg(0.U(XLEN.W), fullMask, "h888".U(XLEN.W)), // MRW
    
    // M-mode Debug/Trace Registers
    CsrAddr.TSELECT    -> new CsrReg(1.U(XLEN.W), fullMask, zeroMask), // MRW
    CsrAddr.TDATA1     -> new CsrReg(0.U(XLEN.W), fullMask, zeroMask), //MRW
    CsrAddr.TDATA2     -> new CsrReg(0.U(XLEN.W), fullMask, zeroMask), //MRW
  )

  csrRegs(CsrAddr.CYCLE).reg := csrRegs(CsrAddr.CYCLE).reg + 1.U

  val rdata = WireInit(0.U(XLEN.W))
  val addrHitRead = csrRegs.map { case (csrAddr, _) => addr === csrAddr }.foldLeft(false.B)(_ || _)
  val validCsrAddrRead = addrHitRead

  when(validCsrAddrRead) {
    when(addr === CsrAddr.MSTATUS) {
      val raw_status = csrRegs(CsrAddr.MSTATUS).read()
      val mpp_field = raw_status(12, 11)
      val legal_mpp = readPP(mpp_field)
      rdata := Cat(
        raw_status(XLEN-1, 13),
        legal_mpp,
        raw_status(10, 0)
      )
    }.otherwise {
      rdata := MuxLookup(addr, 0.U(XLEN.W))(
        csrRegs.map { case (csrAddr, csrReg) => csrAddr -> csrReg.read() }.toSeq
      )
    }
  } .otherwise {
    rdata := 0.U(XLEN.W)
  }

  val write_enable = WireInit(false.B)
  val csr_write_data = Wire(UInt(XLEN.W))
  val illegal_write = WireInit(false.B)
  val illegal_mode = WireInit(false.B)
  val illegal_access = WireInit(false.B)
  val illegal_addr = WireInit(false.B)

  val opType = io.info.op(2, 0)
  val csr_write = io.info.valid && io.info.fusel === FuType.csr && CSROpType.isCSROp(io.info.op(3, 0))
  
  when(csr_write) {
    val is_rs1_zero = src1_data === 0.U
    val is_imm_zero = csri(4,0) === 0.U
    val only_read = MuxLookup(opType, false.B)(Seq(
      CSROpType.write(2, 0)  -> false.B,
      CSROpType.set(2, 0)    -> is_rs1_zero,
      CSROpType.clear(2, 0)  -> is_rs1_zero,
      CSROpType.writei(2, 0) -> false.B,
      CSROpType.seti(2, 0)   -> is_imm_zero,
      CSROpType.cleari(2, 0) -> is_imm_zero
    ))
    
    val priv_ok = (addr(9, 8) <= mode)
    illegal_mode := !priv_ok
    
    val is_write_ro_csr = (addr(11, 10) === "b11".U) && !only_read
    illegal_write := is_write_ro_csr
    
    val valid_addr = validCsrAddrRead
    illegal_addr := !valid_addr
    
    illegal_access := illegal_mode || illegal_write || illegal_addr
    
    write_enable := !illegal_access && !only_read && valid_addr
  }

  csr_write_data := MuxCase(0.U, Seq(
    (opType === CSROpType.write(2, 0))  -> src1_data,
    (opType === CSROpType.set(2, 0))    -> (rdata | src1_data),
    (opType === CSROpType.clear(2, 0))  -> (rdata & (~src1_data)),
    (opType === CSROpType.writei(2, 0)) -> csri,
    (opType === CSROpType.seti(2, 0))   -> (rdata | csri),
    (opType === CSROpType.cleari(2, 0)) -> (rdata & (~csri))
  ))

  when(validCsrAddrRead && write_enable) {
    for ((csrAddr, csrReg) <- csrRegs) {
      when(addr === csrAddr) {
        when(csrAddr === CsrAddr.MSTATUS) {
          val raw_mpp = csr_write_data(12, 11)
          val legal_mpp = writePP(raw_mpp)
          val modified_data = Cat(
            csr_write_data(XLEN-1, 13),
            legal_mpp,
            csr_write_data(10, 0)
          )
          csrReg.write(modified_data)
        }.otherwise {
          csrReg.write(csr_write_data)
        }
      }
    }
  }
  
  def readPP(pp: UInt): UInt = {
    MuxCase(Priv.u, Seq(
      (pp === Priv.m) -> Priv.m,
      (pp === Priv.u) -> Priv.u
    ))
  }
  
  def writePP(pp: UInt): UInt = {
    Mux(pp === Priv.m || pp === Priv.u, pp, Priv.u)
  }
  
  val ex_out = WireInit(io.ex)
  
  val csr_illegal_access_exception = WireInit(false.B)
  val csr_illegal_access_tval = WireInit(0.U(XLEN.W))

  when(io.info.valid && io.info.fusel === FuType.csr && CSROpType.isCSROp(io.info.op(3, 0))) {
    when(illegal_access) {
      csr_illegal_access_exception := true.B
      csr_illegal_access_tval := io.info.inst
    }
  }
  
  val illegal_inst_flag = io.ex.exception(illegalInst) || csr_illegal_access_exception
  val decode_only_illegal = io.ex.exception(illegalInst) && !csr_illegal_access_exception
  
  val exception_bits = ex_out.exception.asUInt
  val updated_exception_bits = exception_bits | (illegal_inst_flag << illegalInst.U)
  
  val final_ex_out = Wire(new ExceptionInfo())
  final_ex_out.exception := updated_exception_bits.asTypeOf(ex_out.exception)
  final_ex_out.interrupt := ex_out.interrupt
  
  for (i <- 0 until EXC_WID) {
    when(i.U === illegalInst.U) {
      when(csr_illegal_access_exception) {
        final_ex_out.tval(i) := csr_illegal_access_tval
      }.elsewhen(decode_only_illegal) {
        final_ex_out.tval(i) := ex_out.tval(i)
      }.otherwise {
        final_ex_out.tval(i) := ex_out.tval(i)
      }
    }.otherwise {
      final_ex_out.tval(i) := ex_out.tval(i)
    }
  }
  
  val mip = csrRegs(CsrAddr.MIP)
  val new_mip = mip.read() | 
    (io.interrupt.msi << 3) | 
    (io.interrupt.mti << 7) | 
    (io.interrupt.mei << 11)
  mip.write(new_mip)
  
  val mstatus = csrRegs(CsrAddr.MSTATUS)
  val mie = csrRegs(CsrAddr.MIE)
  
  val mstatus_mie = mstatus.read()(3)
  
  val mie_meie = mie.read()(11)
  val mie_mtie = mie.read()(7)
  val mie_msie = mie.read()(3)
  
  val mip_meip = new_mip(11)
  val mip_mtip = new_mip(7)
  val mip_msip = new_mip(3)
  
  val m_mei_can_int = mode < Priv.m || (mode === Priv.m && mstatus_mie)
  val m_mti_can_int = mode < Priv.m || (mode === Priv.m && mstatus_mie)
  val m_msi_can_int = mode < Priv.m || (mode === Priv.m && mstatus_mie)
  
  val mei_int_valid = m_mei_can_int && mie_meie && mip_meip
  val mti_int_valid = m_mti_can_int && mie_mtie && mip_mtip
  val msi_int_valid = m_msi_can_int && mie_msie && mip_msip
  
  val interrupts = WireInit(VecInit(Seq.fill(INT_WID)(false.B)))
  interrupts(3) := msi_int_valid
  interrupts(7) := mti_int_valid
  interrupts(11) := mei_int_valid
  io.interrupt_out := interrupts
  
  val is_ecall = io.info.valid && io.info.fusel === FuType.csr && io.info.op(3, 0) === CSROpType.ecall
  val is_ebreak = io.info.valid && io.info.fusel === FuType.csr && io.info.op(3, 0) === CSROpType.ebreak
  val is_mret = io.info.valid && io.info.fusel === FuType.csr && io.info.op(3, 0) === CSROpType.mret
  
  val actual_exceptions_present = final_ex_out.exception.asUInt.orR || final_ex_out.interrupt.asUInt.orR
  val has_exception_signal = io.info.valid && (actual_exceptions_present || is_ecall || is_ebreak || is_mret)
  io.has_exception := has_exception_signal

  val has_int = io.info.valid && final_ex_out.interrupt.asUInt.orR
  val has_exc = io.info.valid && final_ex_out.exception.asUInt.orR
  
  val exceptionNO = PriorityMux(
    final_ex_out.exception.zipWithIndex.map { case (e, i) => (e, i.U(4.W)) }
  )
  
  val interruptNO = PriorityMux(
    final_ex_out.interrupt.zipWithIndex.map { case (i, idx) => (i, idx.U(4.W)) }
  )
  
  val causeNO = Mux(has_int, interruptNO, 
               Mux(has_exc, exceptionNO,
               Mux(is_ecall, Mux(mode === Priv.m, 11.U, 8.U),
               Mux(is_ebreak, 3.U, 0.U))))
  
  val mtval_data = Mux(has_exc, final_ex_out.tval(exceptionNO), 0.U)
  
  val flush_signal = WireInit(false.B)
  val target_signal = WireInit(0.U(XLEN.W))
  val next_mode_signal = WireInit(mode)
  
  when(has_exception_signal && !is_mret) {
    flush_signal := true.B
    
    val mcause_data = Cat(has_int, 0.U((XLEN-5).W), causeNO)
    csrRegs(CsrAddr.MCAUSE).write(mcause_data)
    csrRegs(CsrAddr.MEPC).write(io.pc)
    csrRegs(CsrAddr.MTVAL).write(mtval_data)
    
    val old_mstatus = mstatus.read()
    val current_mode_for_mpp = mode
    val legal_current_mode_for_mpp = writePP(current_mode_for_mpp)
    
    val new_mstatus = Cat(
      old_mstatus(XLEN-1, 13),                 
      legal_current_mode_for_mpp,
      old_mstatus(10, 8),                       
      old_mstatus(3),
      old_mstatus(6, 4),                        
      0.U(1.W),
      old_mstatus(2, 0)                         
    )
    mstatus.write(new_mstatus)
    
    val base = Cat(csrRegs(CsrAddr.MTVEC).read()(XLEN-1, 2), 0.U(2.W))
    val vector_mode = csrRegs(CsrAddr.MTVEC).read()(0) && has_int
    val vector_offset = Mux(has_int, (causeNO << 2), 0.U)
    target_signal := base + Mux(vector_mode, vector_offset, 0.U)
    
    next_mode_signal := Priv.m
  }.elsewhen(is_mret) {
    flush_signal := true.B
    
    val old_mstatus = mstatus.read()
    val mpp_raw = old_mstatus(12, 11)
    
    val mpp = readPP(mpp_raw)
    
    val new_mstatus = Cat(
      old_mstatus(XLEN-1, 13),
      Priv.u,
      old_mstatus(10, 8),
      1.U(1.W),
      old_mstatus(6, 4),
      old_mstatus(7),
      old_mstatus(2, 0)
    )
    mstatus.write(new_mstatus)
    
    target_signal := csrRegs(CsrAddr.MEPC).read()
    
    next_mode_signal := mpp
  }
  
  mode := next_mode_signal
  
  val reg_wen_out_signal = io.info.reg_wen && !has_exception_signal

  io.result := rdata
  io.ex_out := final_ex_out
  io.flush := flush_signal
  io.target := target_signal
  io.reg_wen_out := reg_wen_out_signal
}