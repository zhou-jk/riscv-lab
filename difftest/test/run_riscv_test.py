#!/usr/bin/env python3

import os

# build riscv-tests to this folder
BUILD_DIR = "./test/asm/riscv-test/isa"
DST_DIR = "./test/bin/riscv-test"
TEST_PRIFIX = ["rv64ui-p-", "rv64um-p-", "rv64mi-p-", "rv64ua-p-"]
# TEST_PRIFIX += ["rv64si-p-"]
# TEST_PRIFIX += ["rv64ui-v-", "rv64um-v-", "rv64ua-v-"]
RISCV_PREFIX = "riscv64-unknown-linux-gnu-"

file_list = []
for dirpath, dirnames, filenames in os.walk(BUILD_DIR):
    for x in filenames:
        if x.endswith(".dump"):
            continue
        for y in TEST_PRIFIX:
            if x.startswith(y):
                file_list.append(x)
file_list.sort()


def run_all():
    for x in file_list:
        print("Testing {}: ".format(x), end="", flush=True)
        os.system("./obj_dir/Vtop {}/{}.bin -rvtest".format(DST_DIR, x))


run_all()
