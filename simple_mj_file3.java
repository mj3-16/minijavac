class A {

  public void run() {
    System.out.println(-run2());
  }

  public int run2() {
    return 2147483587;
  }

  public static void main(String[] args) {
    new A().run();
  }
}
