package serializable;

import java.io.*;
import java.net.URISyntaxException;


public class SerializableTest {
    public static void main(String[] args) throws IOException, URISyntaxException {
        // UserNotSerializable user = new UserNotSerializable();
        // Exception in thread "main" java.io.NotSerializableException: serializable.UserNotSerializable
        // 	at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1184)
        // 	at java.io.ObjectOutputStream.writeObject(ObjectOutputStream.java:348)
        // 	at serializable.SerializableTest.main(SerializableTest.java:21)

        User user = new User();
        user.setUid(1);
        user.setName("Joe");
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(new File("user.txt")))) {
            objectOutputStream.writeObject(user);
        };
    }
}

/**

 *
 * java.net 该包提供实现网络应用与开发的类。
 * URL类
 * URL代表一个统一资源定位符，它是指向互联网“资源”的指针。
 * 资源可以是简单的文件或目录，也可以是对更为复杂的对象的引用，例如对数据库或搜索引擎的查询。
 * ...
 *
 * ① 都是 java.net 包下的。
 * ② URL 是 URI 的一个子集，URL是 URI 其中的一种。
 * ③ URI 类在某些特定的情况下，会对其组成的字段执行转义。
 * 建议使用 URI 管理 URL 的编码和解码，并使用 URI.toURL() 和 URL.toURI() 进行转换。
 */
