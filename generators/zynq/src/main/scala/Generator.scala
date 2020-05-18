package zynq

import scala.util.Try

import chisel3._

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.util.{GeneratorApp}
import freechips.rocketchip.system.{TestGeneration}

object Generator extends GeneratorApp {
//  override lazy val longName = names.topModuleClass + "." + names.configs
  // use same file naming scheme as example in order to be able to use makefiles
  override lazy val longName = names.topModuleProject + "." + names.topModuleClass + "." + names.configs
  generateFirrtl
  generateAnno
}
