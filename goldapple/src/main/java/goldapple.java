import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.io.IOException;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.tuples.generated.Tuple11;
import org.web3j.tuples.generated.Tuple14;
import org.web3j.utils.Convert;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
//import org.web3j.protocol.core.methods.request.Transaction;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import goldapple.FoMo3Dlong;

public class goldapple {
    private static Logger log = LoggerFactory.getLogger("alog");


    public static void main(String[] args) throws Exception {
        new goldapple().run();
    }

    // GAS价格
    public static BigInteger GAS_PRICE = BigInteger.valueOf(10_000_000_000L);

    // GAS上限
    public static BigInteger GAS_LIMIT = BigInteger.valueOf(65_00_000L);

    // 账户密码
    public static String PASSWORD = "ethkeyis.*@101010";

    // 账户文件路径
    public static String PATH = "UTC--2018-09-29T15-21-18.912Z--4df6428de52d80e359c8fe72e22f4487bdad9de3";

    // 合约地址，第一次部署之后记录下来
    public static String ADDRESS = "0xAb83D96de35bAD6F234178FBb6507203488E9626";

    public static BigInteger KEYPEICE;

    public static String account;

    public static FoMo3Dlong contract;

    private void run() throws Exception {

        // We start by creating a new web3j instance to connect to remote nodes on the network.
        // Note: if using web3j Android, use Web3jFactory.build(...
        Web3j web3j = Web3j.build(new HttpService("https://mainnet.infura.io/v3/08a75479c11741cab53971363da9abb7"));
        //System.out.println("Connected to Ethereum client version: " + web3j.web3ClientVersion().send().getWeb3ClientVersion());
        log.info("Connected to Ethereum client version:{} " , web3j.web3ClientVersion().send().getWeb3ClientVersion());
        Credentials credentials = WalletUtils.loadCredentials(PASSWORD, PATH);
        account = credentials.getAddress();


        contract = FoMo3Dlong.load(ADDRESS, web3j, credentials, GAS_PRICE, GAS_LIMIT);

        //获取当前区块
        EthBlockNumber BlockNumber = web3j.ethBlockNumber().send();
        BigInteger blocknumber = BlockNumber.getBlockNumber();
        log.info("当前区块：{}", blocknumber);

        new Thread(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                try {
                    buyIn(web3j,contract);
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }).start();

    }

    public static BigInteger getNonce(Web3j web3j, String addr) {
        try {
            EthGetTransactionCount getNonce = web3j.ethGetTransactionCount(addr, DefaultBlockParameterName.PENDING).send();

            if (getNonce == null){
                throw new RuntimeException("net error");
            }
            return getNonce.getTransactionCount();
        } catch (IOException e) {
            throw new RuntimeException("net error");
        }
    }

    public static BigDecimal getBalance(Web3j web3j, String address) {
        try {
            EthGetBalance ethGetBalance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
            return Convert.fromWei(new BigDecimal(ethGetBalance.getBalance()),Convert.Unit.ETHER);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public BigInteger getGasPrice(BigInteger gas, int retryCount) {

        // 增加交易手续费失败了增加交易
        float multiple = (float) (retryCount + 1.5);
        BigInteger gasReal = gas.multiply(BigDecimal.valueOf(multiple).toBigInteger());
        BigInteger gasMax = Convert.toWei("50", Convert.Unit.GWEI).toBigInteger();

        // 防止手续费太多
        if (gasReal.compareTo(gasMax) >= 0) {
            gasReal = gasMax;
        }
        log.info("retry count: {} multiple: {} gas: {} gas real: {}", retryCount, multiple, gas, gasReal);
        return gasReal;
    }

    public String getCurrentLeader(FoMo3Dlong contract) throws Exception{
        try {
            Tuple11<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, String, byte[], BigInteger, BigInteger> result = contract.getCurrentRoundInfo().send();
            if(result.getValue8() == null)
                throw new RuntimeException("net error");
            return result.getValue8();
        }catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    public void buyIn(Web3j web3j, FoMo3Dlong contract) throws Exception{

        int count = 0;
        int retryCount = 0;
        //剩余时间小于10秒
        while(true){
            count++;
            log.info("===================开始第{}次检查=====================",count);
            //获取待确认区块信息
            DefaultBlockParameter status = DefaultBlockParameter.valueOf("pending");
            EthBlock.Block block = web3j.ethGetBlockByNumber(status, true).send().getBlock();
            List<EthBlock.TransactionResult> transactions = block.getTransactions();
            int num = transactions.size();
            log.info("交易数量：{} ", num);

            //获取当前keys价格
            KEYPEICE = contract.getBuyPrice().send();
            log.info("当前价格：{} " , KEYPEICE);

            for(EthBlock.TransactionResult<Transaction> transaction : transactions)
            {
                String to = transaction.get().getTo();

                if(to != null&&to.toUpperCase() == ADDRESS.toUpperCase())
                {
                    BigInteger value = transaction.get().getValue();
                    BigInteger gasPrice = transaction.get().getGasPrice();
                    GAS_PRICE = gasPrice.multiply(BigInteger.valueOf(2));
                    log.info("已有人购买数量：{} ", value.toString());
                }

            }

            // 限制重试次数
            if( retryCount >= 3 ) {
                retryCount = 0;
            }

            //获取剩余时间
            BigInteger timeleft = contract.getTimeLeft().send();
            String leadingAddr = getCurrentLeader(contract);
            log.info("剩余时间: {}", timeleft.toString());
            if(timeleft.compareTo(BigInteger.valueOf(30))>0){
                log.info("大于30秒，继续等待 ");
                retryCount=0;
            }
            else if(leadingAddr.compareToIgnoreCase(account) == 0){
                log.info("<<<<<<<<<< 恭喜你持有最后一个key,持有地址 {} ", account);
                retryCount=0;
            }else{
                log.info("开始抢key");
                String reuslt = buyAction(web3j, contract, retryCount);

                if(!reuslt.isEmpty()){
                    retryCount=0;
                }
                else{
                    retryCount++;
                }

            }

            // 重试为0 暂停timeleft/10秒
            if( retryCount == 0 ) {

                long waitTime = timeleft.divide(BigInteger.valueOf(10)).longValue();
                waitTime = (waitTime > 3) ? waitTime : 3;
                log.info("<<<<<<<<<<<<<继续等待 {} 秒", waitTime);
                Thread.sleep(waitTime * 1000);
            }

        }

    }

    public String buyAction(Web3j web3j, FoMo3Dlong contract, int retryCount) throws Exception {

        //获取余额
        BigInteger balance = getBalance(web3j,account).toBigInteger();

        if(balance.compareTo(KEYPEICE)<0)
        {
            throw new RuntimeException("余额不足，请核实");
        }
        //获取nonce
        BigInteger nonce = getNonce(web3j,account);

        //输入输出参数
        Address  affcode = new Address("0xA62142888ABa8370742bE823c1782D17A0389Da1");
        Uint256 team = new Uint256(1);
        Uint256 value = new Uint256(KEYPEICE.multiply(BigInteger.valueOf(101).divide(BigInteger.valueOf(100))));

        float multiple = (float) (retryCount + 1);
        GAS_PRICE = getGasPrice(GAS_PRICE, retryCount);

        //抢购
        //TransactionReceipt transactionReceipt = contract.buyXaddr(affcode,team, value).send();



        //List<Type> inputParameters = new ArrayList<>();
        //inputParameters.add(affcode);
        //inputParameters.add(team);
        //inputParameters.add(value);
        //Transacting with a smart contract
        Function function = new Function(
                "buyXaddr",  // function we're calling
                Arrays.<Type>asList(affcode, team, value),  // Parameters to pass as Solidity Types
                Collections.<TypeReference<?>>emptyList());

        String encodedFunction = FunctionEncoder.encode(function);
        org.web3j.protocol.core.methods.request.Transaction  transaction = org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
                account, nonce, GAS_PRICE, GAS_LIMIT, ADDRESS, BigInteger.ONE, encodedFunction);

        org.web3j.protocol.core.methods.response.EthSendTransaction transactionResponse =
                web3j.ethSendTransaction(transaction).sendAsync().get();

        String transactionHash = transactionResponse.getTransactionHash();

        log.info("交易哈希：{}", transactionHash);
        return transactionHash;
    }


}
