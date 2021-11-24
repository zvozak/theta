extern void reach_error();

int function() {
    return -1;
}

int main() {
    int ret = function();
    if(ret == 0) return reach_error();
}