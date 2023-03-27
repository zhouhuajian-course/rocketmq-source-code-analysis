package atomicReference;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class BankCardTest03 {
    // private static volatile BankCard bankCard = new BankCard("张三",0);
    private static AtomicReference<BankCard> bankCardRef = new AtomicReference<>(new BankCard("张三", 0));

    public static void main(String[] args) throws InterruptedException {
        // 开启10个线程 每人给张三增加100元
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                while (true) {

                    // 先读取全局的引用
                    // 获取引用
                    final BankCard card = bankCardRef.get();
                    // 构造一个新的账户，存入原来+100的钱
                    BankCard newCard = new BankCard(card.getAccountName(), card.getMoney() + 100);
                    // 最后把新的账户的引用赋给原账户
                    // CAS乐观锁 修改引用
                    // bankCardRef.set();
                    if (bankCardRef.compareAndSet(card, newCard)) {
                        log.debug(bankCardRef.get().toString());
                        break;
                    }
                    try {
                        TimeUnit.MICROSECONDS.sleep(1000);
                        // TimeUnit.SECONDS.sleep(1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
        Thread.sleep(10000);
        log.debug(bankCardRef.get().toString());
    }
}
