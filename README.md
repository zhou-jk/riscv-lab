```

██████╗ ██╗███████╗ ██████╗██╗   ██╗      ██╗      █████╗ ██████╗
██╔══██╗██║██╔════╝██╔════╝██║   ██║      ██║     ██╔══██╗██╔══██╗
██████╔╝██║███████╗██║     ██║   ██║█████╗██║     ███████║██████╔╝
██╔══██╗██║╚════██║██║     ╚██╗ ██╔╝╚════╝██║     ██╔══██║██╔══██╗
██║  ██║██║███████║╚██████╗ ╚████╔╝       ███████╗██║  ██║██████╔╝
╚═╝  ╚═╝╚═╝╚══════╝ ╚═════╝  ╚═══╝        ╚══════╝╚═╝  ╚═╝╚═════╝


```

# 🚀 [RISCV-LAB](https://code.educoder.net/ppg69fuwb/riscv-lab)

从零开始的 RV64IMAZicsr_Zifencei 流水线设计实验

## 📚 简介

- 支持 RV64IMAZicsr_Zifencei 指令集的顺序动态双发射五级流水线
- 可接入差分测试框架，提供软件仿真测试
- 面向教学的实验设计，请搭配实验指导手册食用

## 🎬 目录结构

```bash
.
├── .git # 用于存储Git版本库
├── .gitignore # 用于配置Git忽略文件
├── .vscode # 用于配置VSCode编辑器
├── README.md # 项目说明文档
├── chisel # Chisel代码
│ ├── .bloop # 用于配置Bloop编译器
│ ├── .gitignore # 用于配置Git忽略文件
│ ├── .metals # 用于配置Metals编译器
│ ├── .scalafmt.conf # 用于配置Scalafmt代码格式化工具
│ ├── .vscode # 用于配置VSCode编辑器
│ ├── Makefile # 用于配置Make编译工具
│ ├── README.md # 项目说明文档
│ ├── build # 用于存储Chisel生成的Verilog文件
│ ├── build.sc # 用于配置Mill编译工具
│ ├── out # 用于存储mill编译后的文件
│ ├── playground
│ │ ├── resources # 用于存储CPU设计相关的Verilog文件
│ │ ├── src # 用于存储CPU设计相关的Chisel文件
│ │ └── test # 用于存储CPU设计相关的Chisel测试文件
│ └── utils # 用于存储firtool相关的文件
├── difftest # 差分测试框架
```

## 🛠️ 环境配置

### 🍕 安装 Verilator

```bash
sudo apt-get install git help2man perl python3 make autoconf g++ flex bison ccache
sudo apt-get install libgoogle-perftools-dev numactl perl-doc
sudo apt-get install libfl2 # 如果报错忽略即可
sudo apt-get install libfl-dev # 如果报错忽略即可
sudo apt-get install zlibc zlib1g zlib1g-dev # 如果报错忽略即可

cd ~
git clone https://github.com/verilator/verilator # 拉取 verilator 的仓库，执行过一次即可

unset VERILATOR_ROOT
cd verilator
git checkout v5.010 # 推荐安装使用 5.010 版本
autoconf
./configure
make -j `nproc` # 如果报错请尝试直接输入 make 命令
sudo make install

# 经过以上步骤，verilator应该已经安装完毕，可以在终端中输入以下命令测试是否安装成功：
verilator –-version
# 如果正确的输出了版本号，则说明安装成功；如果报错，可以尝试重新安装。
```

### 🍔 安装 GTKWave

```bash
sudo apt-get install gtkwave

# 使用方法为
gtkwave <波形文件的路径>
```

### 🍟 安装 Java

```bash
sudo apt-get install openjdk-11-jdk
java --version # 若安装成功将输出对应版本号
```

### 🌭 安装 Mill

```bash
curl -L https://github.com/com-lihaoyi/mill/releases/download/0.11.6/0.11.6 > mill && chmod +x mill
mv mill /usr/bin/mill
mill --version # 若安装成功将输出对应版本号
```

### 🍖 安装 VS Code

- 安装 VS Code
- 安装 Scala（Metals）插件
- 安装 Todo Tree 插件
- 安装 Git Graph 插件

### 🥓 安装实验框架

```bash
git clone https://bdgit.educoder.net/ppg69fuwb/riscv-lab.git
cd riscv-lab

git config --global user.name "244050090-Zhang San" # 设置学号和姓名
git config --global user.email "zhangsan@foo.com"   # 设置邮箱
git config --global core.editor vim                 # 设置文本编辑器
git config --global color.ui true

bash init.sh difftest
source ~/.bashrc
echo $RVDIFF_HOME # 查看是否成功输出 difftest 文件夹的路径

cd difftest
make verilog # 若在 chisel/build 目录下成功生成 verilog 文件（或得到信号名未被完全初始化的报错提示），说明环境安装成功
```

## 📢 注意事项

- 编程位置位于 chisel 中
- 测试位置位于 difftest 中
- 进入 difftest 目录后
  - 使用 make verilog 生成 verilog 代码（首次实验需要按实验要求补充代码，否则会报错）
  - 使用 make lab1 进行 实验 1 的测试
  - 使用 make trace_lab1 进行 实验 1 的 CPU 测试记录生成
  - 实验 2 为 lab2，以此类推
  - difftest 目录下的 trace.txt 文件为测试结果，用于提交头歌平台，作为评分依据
- 在实验时务必确认 git 下有产生实验记录，这是重要的采分点之一，可通过 `git log tracer-rvlab` 查看（如果没有产生实验记录，请检查RVDIFF_HOME是否设置正确）

## 📦 资源

- 🧰[RISC-V Convertor](https://luplab.gitlab.io/rvcodecjs/) - RISC-V 汇编转换器
- 📑[Chisel Project Template](https://github.com/OSCPU/chisel-playground) - Chisel 项目模板
