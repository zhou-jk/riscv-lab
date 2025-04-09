package cpu

import chisel3._
import chisel3.util._

import defines._
import defines.Const._
import pipeline._

class Core extends Module {
  val io = IO(new Bundle {
    val interrupt = Input(new ExtInterrupt())
    val instSram  = new InstSram()
    val dataSram  = new DataSram()
    val debug     = new DEBUG()
  })

  val fetchUnit      = Module(new FetchUnit()).io
  val decodeStage    = Module(new DecodeStage()).io
  val decodeUnit     = Module(new DecodeUnit()).io
  val regfile        = Module(new ARegFile()).io
  val executeStage   = Module(new ExecuteStage()).io
  val executeUnit    = Module(new ExecuteUnit()).io
  val memoryStage    = Module(new MemoryStage()).io
  val memoryUnit     = Module(new MemoryUnit()).io
  val writeBackStage = Module(new WriteBackStage()).io
  val writeBackUnit  = Module(new WriteBackUnit()).io

  // 取指单元
  fetchUnit.instSram <> io.instSram
  fetchUnit.decodeStage <> decodeStage.fetchUnit
  
  fetchUnit.branch := executeUnit.branch
  fetchUnit.target := executeUnit.target

  // 完成Core模块的逻辑
  decodeStage.decodeUnit <> decodeUnit.decodeStage

  regfile.read.src1.raddr := decodeUnit.regfile.src1.raddr
  regfile.read.src2.raddr := decodeUnit.regfile.src2.raddr

  decodeUnit.regfile.src1.rdata := regfile.read.src1.rdata
  decodeUnit.regfile.src2.rdata := regfile.read.src2.rdata
  decodeUnit.executeStage <> executeStage.decodeUnit

  executeStage.executeUnit <> executeUnit.executeStage
  executeUnit.memoryStage  <> memoryStage.executeUnit

  memoryStage.memoryUnit <> memoryUnit.memoryStage

  io.dataSram.rdata <> memoryUnit.rdata

  memoryUnit.writeBackStage <> writeBackStage.memoryUnit

  executeUnit.dataSram <> io.dataSram

  writeBackStage.writeBackUnit <> writeBackUnit.writeBackStage

  regfile.write.wen   := writeBackUnit.regfile.wen
  regfile.write.waddr := writeBackUnit.regfile.waddr
  regfile.write.wdata := writeBackUnit.regfile.wdata

  io.debug <> writeBackUnit.debug
}