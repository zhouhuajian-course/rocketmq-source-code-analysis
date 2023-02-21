package enumTest;

public class EnumTest {
    public static void main(String[] args) {
        System.out.println(SerializeType.JSON);  // JSON
        System.out.println(Color.RED);  // RED 序号 0
        System.out.println(Color.RED.ordinal());  // RED 序号 0
        System.out.println(Color.GREEN.ordinal()); // 序号 1
    }
}
