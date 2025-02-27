
#Command to copy paste
#py .\script.py --file stmt_if.py --outputType reference
#File can change to any file within \test\data\pa3\sample\
#outputType should be student or reference
#py .\script.py --file test.py --outputType reference --type out

import subprocess
import sys
import argparse
import platform
os_type = ":"
if platform.system() == "Windows":
    os_type = ";"
def run_command(command):
    subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, shell=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(prog='Test Harness',description="Python CS164 Test harness script")
    parser.add_argument("-f", "--file",required = True, help = "Provide the python file to run the tester on")
    parser.add_argument("-o", "--outputType", required = True, choices = ["student", "reference"], help = "Choose from ref or student")
    parser.add_argument("-t", "--type", required = False, choices = ["out", "sample", "benchmark"], help = "Chooses what file to test from")

    run_command("mvn clean package")
    args = parser.parse_args()
    # print(args) #index: 0 is file name, start with index 1
    path = "src/test/data/pa3/sample/"
    if args.type == "out":
        path = ""
    elif args.type == "benchmark":
        path = "./src/test/data/pa3/benchmark/"
    # print(path)


#     print(args)
    outType = "r"

    if args.outputType == "student":
        outType = "s"
    chocopy_input_file = path + args.file
    ast_json_file = args.file + ".out." + args.outputType
    typed_ast_json_file = ast_json_file + ".typed." + args.outputType
    assembly_file = typed_ast_json_file + ".s." + args.outputType

    command1 = f'java -cp "chocopy-ref.jar{os_type}target/assignment.jar" chocopy.ChocoPy --pass=r {chocopy_input_file} --out {ast_json_file}'
    command2 = f'java -cp "chocopy-ref.jar{os_type}target/assignment.jar" chocopy.ChocoPy --pass=.r ./{ast_json_file} --out {typed_ast_json_file}'
    command3 = f'java -cp "chocopy-ref.jar{os_type}target/assignment.jar" chocopy.ChocoPy --pass=..{outType} ./{typed_ast_json_file} --out {assembly_file}'
    command4 = f'java -cp "chocopy-ref.jar{os_type}target/assignment.jar" chocopy.ChocoPy --run ./{assembly_file}'
#     cmd1 = ["java", "-cp", "\"chocopy-ref.jar;target/assignment.jar\"", "chocopy.ChocoPy", "--pass=r", path+args[0], "--out" , args[0]+".out"]
    # run_command("java", arg = cmd1)
    run_command(command1)
    print("Command 1 Done")
    run_command(command2)
    print("Command 2 Done")
    run_command(command3)
    print("Command 3 Done")
#     run_command(command4)
    subprocess.run(command4)
    print("Command 4 Done")
    #subprocess.run(command4)