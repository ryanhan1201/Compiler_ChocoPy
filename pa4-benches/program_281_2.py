a: [int] = None
n: int = 0
i: int = 0
j: int = 0
ret: int = 0


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


n = str_to_int(input())
a = []
while len(a) < n:
    a = a + [str_to_int(input())]

ret = 0
i = 0
j = 0
while i < n:
    j = 0
    while j < i:
        if a[j] < a[i]:
            ret = ret + 1
        j = j + 1
    i = i + 1

print(ret)
