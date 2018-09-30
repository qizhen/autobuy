package  fairbank;
import fairbank.manager.ContractManager;
import fairbank.utils.SysConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.CipherException;
import org.web3j.tuples.generated.Tuple3;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;

/**
 * Hello world!
 *
 */
public class App {
    private static Logger log = LoggerFactory.getLogger("aLog");

    public static enum CONTRACT_TARGET {
        BANK, INFINITY
    }

    public static String getContractAddressForTarget(CONTRACT_TARGET t) {
        switch (t) {
            case BANK:
                return SysConfig.FAIRBANK_CONTRACT_ADDRESS;
            case INFINITY:
                return SysConfig.FARIINFINITY_CONTRACT_ADDRESS;
            default:
                break;
        }
        return null;
    }

    public static int getBuyKeyAccountNumber(CONTRACT_TARGET t) {
        switch (t) {
            case BANK:
                return 0;
            case INFINITY:
                return 1;
            default:
                break;
        }
        return 0;
    }

    public static ArrayList<String> buyKeyTxHashes = new ArrayList<String>();

    public static void main(String[] args) throws IOException, CipherException {
        //String targetString = args[0];
        startAttack("bank");
    }

    public static void startAttack(String targetString) {
        try {
            if (targetString.equalsIgnoreCase("bank")) {

                ////////////////////////////////////////////////////////////////
                // BANK
                ////////////////////////////////////////////////////////////////

                // 抢key
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        try {
                            startBuyKeyLastKey(CONTRACT_TARGET.BANK);
                        } catch (IOException | CipherException | InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }).start();
            } else if (targetString.equalsIgnoreCase("INFINITY")) {

                ////////////////////////////////////////////////////////////////
                // INFINITY
                ////////////////////////////////////////////////////////////////

                // 抢key
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        try {
                            startBuyKeyLastKey(CONTRACT_TARGET.INFINITY);
                        } catch (IOException | CipherException | InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void withdraw(CONTRACT_TARGET t) throws IOException, CipherException {
        if (t == CONTRACT_TARGET.BANK) {
            log.info("========== 提现Bank ==========");
        } else if (t == CONTRACT_TARGET.INFINITY) {
            log.info("========== 提现Infinity ==========");
        }

            String target = getContractAddressForTarget(t);
            int accountNumber = getBuyKeyAccountNumber(t);

            ContractManager.getInstance().retryWithdraw(target, accountNumber);
        }

    public static void startBuyKeyLastKey (CONTRACT_TARGET t) throws IOException, CipherException, InterruptedException {
        if (t == CONTRACT_TARGET.BANK) {
            log.info("========== 开始抢Key BANK ==========");
        } else if (t == CONTRACT_TARGET.INFINITY) {
            log.info("========== 开始抢Key INFINITY ==========");
        }

        int count = 0;
        int retryCount = 0;

        while (true) {
            count++;
            log.info("=============开始第 {} 次检查================", count);

            String target = getContractAddressForTarget(t);
            log.info("合约: {}", target);

            log.info("<<<<<<<<<< Step 1 查看剩余时间");
            Tuple3<BigInteger, BigInteger, BigInteger> res = ContractManager.getInstance().retryCurrentInfo(target, t);
            BigInteger timeEnd = res.getValue1();
            BigInteger amount = res.getValue2();
            BigInteger targetAmount = res.getValue3();
            long timeLeft = timeEnd.longValue() - (System.currentTimeMillis() / 1000);
            String myAddr = t == CONTRACT_TARGET.INFINITY ? SysConfig.ADDRESS1 : SysConfig.ADDRESS;

            log.info("离结束还有 {} 秒", timeLeft);
            log.info("已筹款 {}", amount);
            log.info("筹款目标 {}", targetAmount);

            BigInteger Value = targetAmount.subtract(amount).divide(BigInteger.valueOf(10));
            BigInteger balance = ContractManager.getInstance().retryCurrentBalance();

            log.info("账户余额 {}", balance);
            log.info("花费金额 {}", Value);

            if(balance.compareTo(Value)<=0){
                log.error("余额不足");
            }

            // 限制重试次数
            if (retryCount >= 3) {
                retryCount = 0;
            }

            if (timeLeft > SysConfig.RUSH_SECONDS) {
                log.info("<<<<<<<<<< 时间足够，离结束还有 {} 秒", timeLeft);
                retryCount = 0;
            } else if (Value.longValue() >= 1000000000000000L && Value.longValue() < 1000000000000000000L) {

                log.info("<<<<<<<<<< Step 2 开始抢KEY");

                log.info("buy the {} time", retryCount);
                boolean txResult = buyKey(t, retryCount, Value);

                if (txResult) {
                    // 购买成功，立即开始下一轮检测，并开始合约攻击
                    buyKeyTxHashes.clear();
                    retryCount = 0;
                } else {
                    retryCount++;
                }
            }

            // 重试为0 暂停5秒
            if (retryCount == 0) {

                long waitTime = timeLeft / 10;
                waitTime = (waitTime > 3) ? waitTime : 3;
                log.info("<<<<<<<<<<<<<继续等待{}秒", waitTime);
                Thread.sleep(waitTime * 1000);
            }
        }



    }


    public static boolean buyKey (CONTRACT_TARGET t,int retryCount, BigInteger value) throws
            IOException, CipherException {
        String target = getContractAddressForTarget(t);
        int accountNumber = getBuyKeyAccountNumber(t);

        String txhash = ContractManager.getInstance().retryBuyKey(target, accountNumber, retryCount, value);
        buyKeyTxHashes.add(txhash);
        boolean txResult = ContractManager.getInstance().retryBuyKeyResult(buyKeyTxHashes);

        return txResult;
    }

}
