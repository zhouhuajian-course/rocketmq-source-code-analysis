package atomicReference;

// 银行卡
public class BankCard {
    // 账户名
    private final String accountName;
    // 金额
    private final int money;

    // 构造函数 初始化 账户名和金额
    public BankCard(String accountName,int money){
        this.accountName = accountName;
        this.money = money;
    }
    // 不提供任何个人账号的set方法，只有get方法
    public String getAccountName() {
        return accountName;
    }
    public int getMoney() {
        return money;
    }
    // 重写 toString 方法 方便打印
    @Override
    public String toString() {
        return "BankCard{" +
            "accountName='" + accountName + '\'' +
            ", money='" + money + '\'' +
            '}';
    }
}