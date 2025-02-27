#credit: Avi Mehra for helper str_to_int

def char_to_int(char: str) -> int:
    i: int = 0

    while i < 10:
        if "0123456789"[i] == char:
            return i
        i = i + 1

    return -1


def str_to_int(string: str) -> int:
    result: int = 0
    i: int = 0

    while i < len(string):
        if char_to_int(string[i]) != -1:
            result = result * 10 + char_to_int(string[i])
        i = i + 1

    if string[0] == "-":
        return -result
    return result

def gausssum(n: int) -> int:
    result: int = 0
    counter: int = 1
    
    while n > 0:
        result = result + counter
        counter = counter + 1
        n = n - 1
    
    return result

# Input/output examples
inputvalue: int = 0
outputvalue: int = 0
inputvalue = str_to_int(input())
outputvalue = gausssum(inputvalue)

print(outputvalue)
