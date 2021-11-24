extern void reach_error();

int main() {
    int ret = coin(3,4);
    if(ret == 0) return reach_error();
}