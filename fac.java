class test {
	public static void main(String[] args) {
		int i = 0;
		int j = 1;
		while (i < 10)	{
			i = i + 1;
			j = i * j;
		}
		System.out.println(j);
	}
}
