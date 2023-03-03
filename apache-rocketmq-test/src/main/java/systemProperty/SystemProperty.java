package systemProperty;

public class SystemProperty {
    public static void main(String[] args) throws InterruptedException {
        // java -Duser.home=C:\Users\zhouhuajian ...
        System.out.println(System.getProperty("user.home"));
        Thread.sleep(111111111111L);
    }
}
