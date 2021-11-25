extern void reach_error();

int main() {
    int a = asd();
    int ret = uniform(5);
    if(ret < 2) return reach_error();
    return a;
}