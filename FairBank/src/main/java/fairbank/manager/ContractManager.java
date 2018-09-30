package fairbank.manager;

import com.github.rholder.retry.*;
import fairbank.App.CONTRACT_TARGET;
import fairbank.contract.BContract;
import fairbank.utils.SysConfig;
import fairbank.utils.TUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.CipherException;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tuples.generated.Tuple3;
import org.web3j.tuples.generated.Tuple6;
import org.web3j.tuples.generated.Tuple7;
import org.web3j.tx.Contract;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.utils.Convert;
import org.web3j.utils.Convert.Unit;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ContractManager {
	private static Logger log = LoggerFactory.getLogger("aLog");

	public static ContractManager managerInstance;

	private Web3j web3j;

	private ArrayList<Account> accounts;

	// 普通retryer
	private Retryer<Object> retryer;

	public synchronized static ContractManager getInstance() throws IOException, CipherException {
		if (managerInstance == null) {
			managerInstance = new ContractManager();
		}
		return managerInstance;
	}

	private ContractManager() throws IOException, CipherException {

		// 创建web3j
		web3j = Web3j.build(new HttpService(SysConfig.RPC_ADDRESS));

		// 创建账户
		accounts = new ArrayList<>();

		// account 监控fomo3d最后一个key
		Account account = new Account(web3j, SysConfig.PASSWORD, SysConfig.KEYSTORE);
		accounts.add(account);


		// 初始化重试器
		retryer = RetryerBuilder.<Object>newBuilder().retryIfResult(r -> r == null).retryIfException()
				.withStopStrategy(StopStrategies.stopAfterAttempt(10))
				.withWaitStrategy(WaitStrategies.fixedWait(1000, TimeUnit.MILLISECONDS))
				.withRetryListener(new RetryListener() {
					public <V> void onRetry(Attempt<V> attempt) {
						if (attempt.hasException()) {
							log.error("retry exception {}", attempt.getAttemptNumber());
							attempt.getExceptionCause().printStackTrace();
						}

						if (attempt.hasResult()) {
							log.info("retry result: {} number: {}", attempt.getResult(), attempt.getAttemptNumber());
						}
					}
				}).build();
	}

	private void setFomo3dGasProvider(BigInteger gas, int retryCount) {

		for (Account account : accounts) {
			if (account.getFomo3dContract() != null) {
				account.getFomo3dContract().setGasProvider(new ContractGasProvider() {

					@Override
					public BigInteger getGasPrice(String contractFunc) {

						if (contractFunc.compareTo("buyXaddr") == 0 || contractFunc.compareTo("buyXid") == 0
								|| contractFunc.compareTo("buyXname") == 0) {

							// 增加交易手续费失败了增加交易
							float multiple = (float) (retryCount + 1.5);
							BigInteger gasReal = gas.multiply(BigDecimal.valueOf(multiple).toBigInteger());
							BigInteger gasMax = Convert.toWei("50", Unit.GWEI).toBigInteger();

							// 防止手续费太多
							if (gasReal.compareTo(gasMax) >= 0) {
								gasReal = gasMax;
							}
							log.info("retry count: {} multiple: {} gas: {} gas real: {}", retryCount, multiple, gas,
									gasReal);
							return gasReal;
						}

						return gas;
					}

					@Override
					public BigInteger getGasLimit(String contractFunc) {
						// TODO Auto-generated method stub
						return new BigInteger(Integer.toString(SysConfig.GAS_LIMIT));
					}

					@Deprecated
					public BigInteger getGasPrice() {
						return gas;
					}

					@Deprecated
					public BigInteger getGasLimit() {
						return new BigInteger(Integer.toString(SysConfig.GAS_LIMIT));
					}
				});
			}
		}
	}



	public BigInteger retryCurrentGasPrice() {

		Callable<Object> task = new Callable<Object>() {
			public BigInteger call() throws Exception {
				return getCurrentGasPrice();
			}
		};

		try {
			return (BigInteger) retryer.call(task);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RetryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private BigInteger getCurrentGasPrice() {
		BigInteger minWei = Convert.toWei("1", Unit.GWEI).toBigInteger();

		if (web3j != null) {
			try {
				String hex16 = web3j.ethGasPrice().send().getResult();
				if (!TUtil.isNullOrNot(hex16)) {
					hex16 = hex16.substring(2);
					BigInteger gas = new BigInteger(hex16, 16);

					log.info("Online Line GAS PRICE : " + gas.toString(10));
					if (gas.compareTo(minWei) >= 0) {
						return gas;
					} else {
						return minWei;
					}
				}
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				e.printStackTrace();
			}
		}

		return minWei;
	}

	public BigInteger retryCurrentBalance() {

		Callable<Object> task = new Callable<Object>() {
			public BigInteger call() throws Exception {
				return getCurrentBalance();
			}
		};

		try {
			return (BigInteger) retryer.call(task);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RetryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}


	private BigInteger getCurrentBalance() {
		BigInteger balance = BigInteger.ZERO;
		if (web3j != null) {
			try {
				balance = web3j.ethGetBalance(accounts.get(0).getCredentials().getAddress(), DefaultBlockParameterName.LATEST).send().getBalance();
				log.info("账户余额 : {}" , balance);
				return balance;

			} catch (Exception e) {
				log.error(e.getMessage(), e);
				e.printStackTrace();
			}
		}

		return balance;
	}

	// 攻击合约
	// target 攻击对象
	// 返回txhash集合
	public Tuple3<BigInteger, BigInteger, BigInteger> retryCurrentInfo(String target, CONTRACT_TARGET t) {

		Callable<Object> task = new Callable<Object>() {
			public Tuple3<BigInteger, BigInteger, BigInteger> call() throws Exception {
				return getCurrentInfo(target, t);
			}
		};

		try {
			return (Tuple3<BigInteger, BigInteger, BigInteger>) retryer.call(task);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RetryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private Tuple3<BigInteger, BigInteger, BigInteger> getCurrentInfo(String target, CONTRACT_TARGET t) throws Exception {

		Account account = accounts.get(0);

		BContract fomo3dLongContract = new BContract(target, web3j,
				account.getTransactionManager(), Contract.GAS_PRICE, Contract.GAS_LIMIT);
		account.setFomo3dContract(fomo3dLongContract);

		BigInteger endTime = BigInteger.valueOf(0);
		BigInteger amount = BigInteger.valueOf(0);
		BigInteger targetAmount = BigInteger.valueOf(0);


		if (t == CONTRACT_TARGET.BANK ) {

            Tuple6<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger> currentInfo = fomo3dLongContract.getBankHeadInfo().send();

            endTime = currentInfo.getValue3();
            amount = currentInfo.getValue4();
            targetAmount = currentInfo.getValue5();

		} else if(t == CONTRACT_TARGET.INFINITY){
			Tuple7<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger> currentInfo = fomo3dLongContract.getInfinityHeadInfo().send();

            endTime = currentInfo.getValue6();
            amount = currentInfo.getValue5();
            targetAmount = currentInfo.getValue4();

		}

		Tuple3<BigInteger, BigInteger, BigInteger> result = new Tuple3<BigInteger, BigInteger, BigInteger>(endTime, amount, targetAmount);

		log.info("target {} End Time:{} amounts:{} targetAmount: {}", target,endTime, amount, targetAmount);

		return result;
	}

	public String retryWithdraw(String target, int accountNumber) {

		Callable<Object> task = new Callable<Object>() {
			public String call() throws Exception {
				return withdraw(target, accountNumber);
			}
		};

		try {
			return (String) retryer.call(task);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RetryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private String withdraw(String target, int accountNumber) throws IOException {
		Account account = accounts.get(accountNumber);

		BContract fomo3dLongContract = new BContract(target, web3j,
				account.getTransactionManager(), Contract.GAS_PRICE, Contract.GAS_LIMIT);
		account.setFomo3dContract(fomo3dLongContract);

		setFomo3dGasProvider(retryCurrentGasPrice(), 0);

		String txhash = account.getWithdrawTransactionHash();
		log.info("target {} send the withdraw transaction:{}", target, txhash);

		// send the transaction
		account.getFomo3dContract().withdraw().sendAsync();

		return txhash;
	}

	// buy a key
	// 根据重试次数，增加交易手续费，每次都是上一次的两倍
	// 异步调用，并监控交易状态
	public String retryBuyKey(String target, int accountNumber, int retryCount, BigInteger value) {

		Callable<Object> task = new Callable<Object>() {
			public String call() throws Exception {
				return buyKey(target, accountNumber, retryCount, value);
			}
		};

		try {
			return (String) retryer.call(task);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RetryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private String buyKey(String target, int accountNumber, int retryCount, BigInteger value) throws Exception {
		Account account = accounts.get(accountNumber);

		BContract fomo3dLongContract = new BContract(target, web3j,
				account.getTransactionManager(), Contract.GAS_PRICE, Contract.GAS_LIMIT);
		account.setFomo3dContract(fomo3dLongContract);

		setFomo3dGasProvider(retryCurrentGasPrice(), retryCount);


		String affCode = SysConfig.ADDRESS;
		BigInteger step = BigInteger.valueOf(SysConfig.STEP);
		BigInteger ratio = BigInteger.valueOf(SysConfig.RATIO);
		BigInteger weiValue = value;

		String txhash = account.getBuyTransactionHash(step, ratio, affCode, weiValue);
		log.info("target {} send the buy transaction:{}", target, txhash);

		// send the transaction
		account.getFomo3dContract().buy(step, ratio, affCode, weiValue).sendAsync();

		return txhash;
	}

	public Boolean retryBuyKeyResult(List<String> hashes) {
		Callable<Object> task = new Callable<Object>() {
			public Boolean call() throws Exception {
				return getBuyKeyResult(hashes);
			}
		};

		try {
			return (Boolean) retryer.call(task);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RetryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	// 监控tx的状态
	private Boolean getBuyKeyResult(List<String> hashes) throws Exception {
		long start = System.currentTimeMillis();

		// 循环检查所有交易hash
		while (true) {
			// 时间太久，增加交易手续费，重发
			long curr = System.currentTimeMillis();
			long timeUsed = (curr - start) / 1000;
			log.info("time used: {} seconds", timeUsed);

			if (timeUsed >= SysConfig.RUSH_SECONDS / 3) {
				log.info("pending too long time {} seconds", timeUsed);
				return false;
			}

			// 遍历所有交易，查看是否有交易已经完成
			// 需要重新获取迭代器
			Iterator<String> iterator = hashes.iterator();
			while (iterator.hasNext()) {

				String txhash = iterator.next();

				// 交易hash出错，检查下一个
				if (txhash.length() != 66) {
					log.info("txhash format error");
					continue;
				}

				// 开始检查交易回执
				log.info("==========start check the transaction status {}==========", txhash);

				TransactionReceipt txReceipt = web3j.ethGetTransactionReceipt(txhash).send().getResult();
				if (txReceipt == null) {
					log.info("still wait for tx {} to complete", txhash);
				} else {
					// 得到
					BigInteger gasUsed = txReceipt.getGasUsed();
					String status = txReceipt.getStatus();

					long end = System.currentTimeMillis();
					long seconds = (end - start) / 1000;

					log.info("get the transaction receipt for: {} time used: {} seconds", txhash, seconds);
					if (status.compareToIgnoreCase("0x0") == 0) {
						log.info("buy key fail {}", txhash);
					} else {
						log.info("buy key success {}", txhash);
						log.info("gas used: {}", gasUsed.toString());
						log.info("time used: {} seconds", seconds);

						return true;
					}
				}
			}

			Thread.sleep(2000);
		}
	}
}
