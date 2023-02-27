package serializable;

import java.io.*;
import java.net.URISyntaxException;


public class SerializableTest2 {
    public static void main(String[] args) throws IOException, URISyntaxException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(new File("user.txt")))) {
            Object o = objectInputStream.readObject();
            User user = (User) o;
            System.out.println(user);
            System.out.println(user.getUid());
            System.out.println(user.getName());
        }
    }
}

// 修改 User代码后
// Exception in thread "main" java.io.InvalidClassException: serializable.User; local class incompatible: stream classdesc serialVersionUID = 7057181841028924106, local class serialVersionUID = -2278176205428272874
// 	at java.io.ObjectStreamClass.initNonProxy(ObjectStreamClass.java:699)
// 	at java.io.ObjectInputStream.readNonProxyDesc(ObjectInputStream.java:2028)
// 	at java.io.ObjectInputStream.readClassDesc(ObjectInputStream.java:1875)
// 	at java.io.ObjectInputStream.readOrdinaryObject(ObjectInputStream.java:2209)
// 	at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1692)
// 	at java.io.ObjectInputStream.readObject(ObjectInputStream.java:508)
// 	at java.io.ObjectInputStream.readObject(ObjectInputStream.java:466)
// 	at serializable.SerializableTest2.main(SerializableTest2.java:10)