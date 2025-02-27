inp: str = ""
n: int = 0
table:[str] = None

def strtoint(strin: str) -> int:
    i:int = 0
    j:int = 0
    ichar:str = ""
    jchar:str = ""
    digit:int = 0
    count:int = 0
    acc:int = 0

    while i < len(strin):
        ichar = strin[i]
        digit = -1
        j = 0
        while j < 10:
            jchar = table[j]
            if (ichar == jchar):
                digit = j
            j = j + 1

        if (digit == -1):
            return acc
        acc = acc * 10
        acc = acc + digit
        i = i + 1
    return acc

def fib(n: int) -> int:
    if n == 0:
        return 0
    if n == 1:
        return 1
    return fib(n - 1) + fib(n - 2)

table = ["0","1","2","3","4","5","6","7","8","9"]
inp = input()
n = strtoint(inp)
print(fib(n))
while n != 0:
    fib(n)
    n = n - 1
