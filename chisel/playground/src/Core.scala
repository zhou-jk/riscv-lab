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
  val controlUnit    = Module(new ControlUnit()).io

  // 控制单元
  controlUnit.decodeUnitInfo    := decodeUnit.executeStage.data.info
  controlUnit.executeUnitInfo   := executeUnit.memoryStage.data.info
  controlUnit.memoryUnitInfo    := memoryUnit.writeBackStage.data.info
  controlUnit.writeBackUnitInfo := writeBackUnit.writeBackStage.data.info
  controlUnit.branch            := executeUnit.branch

  // 取指单元
  fetchUnit.instSram <> io.instSram
  fetchUnit.decodeStage <> decodeStage.fetchUnit
  fetchUnit.branch := executeUnit.branch
  fetchUnit.target := executeUnit.target
  fetchUnit.ctrl   := controlUnit.fetchUnitCtrl

  decodeStage.decodeUnit <> decodeUnit.decodeStage
  decodeStage.ctrl := controlUnit.fetchUnitCtrl

  regfile.read.src1.raddr := decodeUnit.regfile.src1.raddr
  regfile.read.src2.raddr := decodeUnit.regfile.src2.raddr

  decodeUnit.regfile.src1.rdata := regfile.read.src1.rdata
  decodeUnit.regfile.src2.rdata := regfile.read.src2.rdata
  decodeUnit.executeStage <> executeStage.decodeUnit

  // 执行阶段
  executeStage.executeUnit <> executeUnit.executeStage
  executeStage.ctrl := controlUnit.decodeUnitCtrl
  executeUnit.memoryStage  <> memoryStage.executeUnit

  // 访存阶段
  memoryStage.memoryUnit <> memoryUnit.memoryStage
  memoryStage.ctrl := controlUnit.executeUnitCtrl

  io.dataSram.rdata <> memoryUnit.rdata
  memoryUnit.writeBackStage <> writeBackStage.memoryUnit

  executeUnit.dataSram <> io.dataSram

  // 写回阶段
  writeBackStage.writeBackUnit <> writeBackUnit.writeBackStage
  writeBackStage.ctrl := controlUnit.writeBackUnitCtrl

  regfile.write.wen   := writeBackUnit.regfile.wen
  regfile.write.waddr := writeBackUnit.regfile.waddr
  regfile.write.wdata := writeBackUnit.regfile.wdata

  io.debug <> writeBackUnit.debug
}