package serializable;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

public class User implements Serializable {
    @Getter
    @Setter
    private int uid;
    @Getter
    @Setter
    private String name;
}
