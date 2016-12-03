#include <stdio.h>
#include <stdlib.h>

#ifdef USE_GC
#include <gc.h>
#endif

void print_int(int val) {
	printf("%d\n", val);
}

void mjMain();

int main() {
    mjMain();
    return 0;
}

void* calloc_impl(size_t num, size_t size){
  #ifdef USE_GC
    return GC_MALLOC(num * size);
  #else
    return calloc(num, size);
  #endif
}
