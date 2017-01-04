#include <stdio.h>
#include <stdlib.h>

#ifdef USE_GC
#include <gc.h>
#endif

void print_int(int val) {
	printf("%d\n", val);
}

void write_int(int val){
    putchar(val);
}

void flush(){
    fflush(stdout);
}

int read_int(){
    return getc(stdin);
}

void mjMain();

int main() {
    mjMain();
    return 0;
}

void* calloc_impl(size_t num, size_t size){
  //printf("num=%zu size=%zu ", num, size);
  // the upper 4 bytes may contain rubish
  // and we don't need to allocate such large memory areas at once
  // we can still allocate around 4 Gbytes
  num = num & 0x00000000ffffffff;
  size = size & 0x00000000ffffffff;
  #ifdef USE_GC
    return GC_MALLOC(num * size);
  #else
    return calloc(num, size);
  #endif
}
