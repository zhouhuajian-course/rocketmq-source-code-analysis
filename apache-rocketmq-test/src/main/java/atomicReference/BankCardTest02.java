package atomicReference;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

// 个人账户类只包含两个字段：accountName 和 money，这两个字段代表账户名和账户金额，账户名和账户金额一旦设置后就不能再被修改。
// 现在假设有多个人分别向这个账户打款，每次存入一定数量的金额，那么理想状态下每个人在每次打款后，该账户的金额会不断增加
// 虽然每次 volatile 都能保证每个账户的金额都是最新的，但是由于上面的步骤中出现了组合操作，即获取账户引用和更改账户引用，每个单独的操作虽然都是原子性的，但是组合在一起就不是原子性的了。所以最后的结果会出现偏差。
// 那么该如何确保获取引用和修改引用之间的线程安全性呢？
// 最简单粗暴的方式就是直接使用 synchronized 关键字进行加锁
// 相较于 BankCardTest ，BankCardSyncTest 增加了 synchronized 锁，运行 BankCardSyncTest 后我们发现能够得到正确的结果。
// 修改 BankCardSyncTest.class 为 bankCard 对象，我们发现同样能够确保线程安全性，这是因为在这段程序中，只有 bankCard 会进行变化，不会再有其他共享数据。
// 如果有其他共享数据的话，我们需要使用 BankCardSyncTest.clas 确保线程安全性。
// 除此之外，java.util.concurrent.atomic 包下的 AtomicReference 也可以保证线程安全性。

@Slf4j
public class BankCardTest02 {
    private static volatile BankCard bankCard = new BankCard("张三",0);

    public static void main(String[] args) {
        // 开启10个线程 每人给张三增加100元
        for(int i = 0;i < 10;i++){
            new Thread(() -> {
                synchronized (bankCard) {
                // synchronized (BankCardSyncTest.class) {
                    // 先读取全局的引用
                    // 获取引用
                    final BankCard card = bankCard;
                    // 构造一个新的账户，存入原来+100的钱
                    BankCard newCard = new BankCard(card.getAccountName(),card.getMoney() + 100);
                    log.debug(newCard.toString());
                    // 最后把新的账户的引用赋给原账户
                    // 修改引用
                    bankCard = newCard;
                    try {
                        TimeUnit.MICROSECONDS.sleep(1000);
                        // TimeUnit.SECONDS.sleep(1);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }
}
