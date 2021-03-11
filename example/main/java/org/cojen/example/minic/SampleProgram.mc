println("Hello world!");

print("Enter name: ");
string name = readLine();

print("Enter age: ");
int age = readInt();

if (age < 10) {
    println("Sorry, you are not old enough to learn about compilers");
    exit();
}

println("Hello " + name);

int n = 10;
int sum = 0;
int i = 1;
while (i <= n) {
    sum = sum + i;
    i = i + 1;
}
println("Sum of the first " + toString(n) +
        " natural numbers: " + toString(sum));

double pi = 3.141592;
int r = 5;
double area = pi * (r * r);
println("Area of a circle with radius " + toString(r) + ": " +
        toString(area));

int desiredCount = 20;
println("First " + toString(desiredCount) + " prime numbers:");
int num = 2;
int count = 0;
while (count < desiredCount) {
    bool isPrime = true;
    int j = 1;
    while (j <= num / 2) {
        if (j != 1 && num % j == 0) {
            isPrime = false;
            break;
        } else
            j = j + 1;
    }
    if (isPrime) {
        print(toString(num));
        if (count < desiredCount - 1)
            print(toString(", "));
        count = count + 1;
    }
    num = num + 1;
}
