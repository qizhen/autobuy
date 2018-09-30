package fairbank.manager;

import fairbank.contract.BContract;
import fairbank.contract.FairBank;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.tx.ChainId;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

public class Account {

	private Web3j web3j;

	private String password;

	private String keystore;

	private Credentials credentials;

	private TransactionManager transactionManager;

	private BContract Contract;

	public Account(Web3j web3j, String password, String keystore) throws IOException, CipherException {
		super();
		this.web3j = web3j;
		this.password = password;
		this.keystore = keystore;

		this.credentials = WalletUtils.loadCredentials(password, keystore);
		this.transactionManager = new RawTransactionManager(web3j, credentials, ChainId.MAINNET);
	}

	public Account(Web3j web3j, Credentials credentials) {
		super();
		this.web3j = web3j;
		this.credentials = credentials;
		this.transactionManager = new RawTransactionManager(web3j, credentials, ChainId.MAINNET);
	}

	public Web3j getWeb3j() {
		return web3j;
	}

	public void setWeb3j(Web3j web3j) {
		this.web3j = web3j;
	}

	public Credentials getCredentials() {
		return credentials;
	}

	public void setCredentials(Credentials credentials) {
		this.credentials = credentials;
	}

	public TransactionManager getTransactionManager() {
		return transactionManager;
	}

	public FairBank getFomo3dContract() {
		return Contract;
	}

	public void setFomo3dContract(BContract Contract) {
		this.Contract = Contract;
	}

	public BigInteger getNonce() throws IOException {
		EthGetTransactionCount ethGetTransactionCount = web3j
				.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING).send();

		return ethGetTransactionCount.getTransactionCount();
	}
	
	public String getWithdrawTransactionHash() throws IOException {
		final Function function = new Function(
				Contract.FUNC_WITHDRAW,
                Arrays.<Type>asList(),
                Collections.<TypeReference<?>>emptyList());
		
		String contractAddress = Contract.getContractAddress();
		String data = FunctionEncoder.encode(function);

		BigInteger gasLimit = Contract.getGasProvider().getGasLimit(function.getName());
		BigInteger gasPrice = Contract.getGasProvider().getGasPrice(function.getName());
		BigInteger nonce = this.getNonce();

		RawTransaction rawTransaction = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, contractAddress, data);

		return TransactionUtils.generateTransactionHashHexEncoded(rawTransaction, ChainId.MAINNET, credentials);
	}

	public String getBuyTransactionHash(BigInteger _stepSize, BigInteger _protectRatio, String _recommendAddr, BigInteger weiValue)
			throws IOException {

		final Function function = new Function(Contract.FUNC_BUY,
				Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_stepSize),
						new org.web3j.abi.datatypes.generated.Uint256(_protectRatio),
						new org.web3j.abi.datatypes.Address(_recommendAddr)),
				Collections.<TypeReference<?>>emptyList());

		String contractAddress = Contract.getContractAddress();
		String data = FunctionEncoder.encode(function);

		BigInteger gasLimit = Contract.getGasProvider().getGasLimit(function.getName());
		BigInteger gasPrice = Contract.getGasProvider().getGasPrice(function.getName());
		BigInteger nonce = this.getNonce();

		RawTransaction rawTransaction = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, contractAddress,
				weiValue, data);

		return TransactionUtils.generateTransactionHashHexEncoded(rawTransaction, ChainId.MAINNET, credentials);
	}
}
