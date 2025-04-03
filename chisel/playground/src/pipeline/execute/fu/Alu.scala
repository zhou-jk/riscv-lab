package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class Alu extends Module {
  val io = IO(new Bundle {
    val info     = Input(new Info())
    val src_info = Input(new SrcInfo())
    val result   = Output(UInt(XLEN.W))
  })
  // 完成ALU模块的逻辑
  
  val opType = io.info.op
  val src1   = io.src_info.src1_data
  val src2   = io.src_info.src2_data

  val src1_32 = src1(31, 0)
  val src2_32 = src2(31, 0)
  val shamt64 = src2(5, 0)
  val shamt32 = src2(4, 0)
  val src1_s = src1.asSInt
  val src2_s = src2.asSInt

  def signExtend32To64(x: UInt): UInt = {
    val x_32 = x(31, 0)
    Cat(Fill(XLEN - 32, x_32(31)), x_32)
  }

  val alu_result = Wire(UInt(XLEN.W))

  alu_result := MuxCase(0.U(XLEN.W), Seq(
    (opType === ALUOpType.add)   -> (src1 + src2),
    (opType === ALUOpType.sub)   -> (src1 - src2),
    (opType === ALUOpType.sll)   -> (src1 << shamt64),
    (opType === ALUOpType.slt)   -> (src1_s < src2_s).asUInt,
    (opType === ALUOpType.sltu)  -> (src1 < src2).asUInt,
    (opType === ALUOpType.xor)   -> (src1 ^ src2),
    (opType === ALUOpType.srl)   -> (src1 >> shamt64),
    (opType === ALUOpType.sra)   -> (src1_s >> shamt64).asUInt,
    (opType === ALUOpType.or)    -> (src1 | src2),
    (opType === ALUOpType.and)   -> (src1 & src2),

    (opType === ALUOpType.addw)  -> {
                                      val result_32 = src1_32 + src2_32
                                      signExtend32To64(result_32)
                                    },
    (opType === ALUOpType.subw)  -> {
                                      val result_32 = src1_32 - src2_32
                                      signExtend32To64(result_32)
                                    },
    (opType === ALUOpType.sllw)  -> {
                                      val result_32 = src1_32 << shamt32
                                      signExtend32To64(result_32)
                                    },
    (opType === ALUOpType.srlw)  -> {
                                      val result_32 = (src1_32.asUInt >> shamt32).asUInt
                                      signExtend32To64(result_32)
                                    },
    (opType === ALUOpType.sraw)  -> {
                                      val result_32 = (src1_32.asSInt >> shamt32).asUInt
                                      signExtend32To64(result_32)
                                    }
  ))

  io.result := alu_result
}
