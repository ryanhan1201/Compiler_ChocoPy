x:int = 0
b:bool = False
temp:int = 0
lst:[int] = None
index:int = 0
def add(num1:int, num2:int) -> int:
    if num1 % 2 == 0:
        return num1 + num2
    else:
        return num1 - num2
def returnFalse() -> bool:
    return False

print(add(0, 1))
print(add(1, 2))
print(b)
print("hello World")

if returnFalse() and True:
    print("Not here")
else:
    if (True or returnFalse()):
        print("Here")

temp = 10 if 1 > 10 else 1
lst = [1, 2, 3, 4]
print(temp)
for index in lst:
    print(index)