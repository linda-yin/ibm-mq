package mqsamples.ptop;

import java.io.Serializable;

/**
 * 此样例提供实现了序列化接口的业务类举例
 */
public class ApplicationData implements Serializable {

    private static final long serialVersionUID = 1L;
    private String name;
    private String hobby;
    private int height;
    private transient int age;//如不需要序列化，使用transient关键字修饰。

    //初始化成员变量
    public ApplicationData(String name) {
        this.name = name;
    }

    public void setHeight(int hight) {
        this.height = hight;
    }

    public int getHeight() {
        return height;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private String getHobby() {
        return hobby;
    }

    public void setHobby(String hobby) {
        this.hobby = hobby;
    }

    private int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "name:" + getName() + ",hobby:" + getHobby() + ",age:" + getAge() + ",Height:" + getHeight();//返回业务类的属性信息
    }
}
