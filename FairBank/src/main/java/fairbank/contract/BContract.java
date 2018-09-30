package fairbank.contract;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;


public class BContract extends FairBank implements IContract{
	
	public BContract(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice,
                          BigInteger gasLimit) {
		super(contractAddress, web3j, credentials, gasPrice, gasLimit);
		// TODO Auto-generated constructor stub
	}

	public BContract(String contractAddress, Web3j web3j, TransactionManager transactionManager,
                          BigInteger gasPrice, BigInteger gasLimit) {
		super(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
		// TODO Auto-generated constructor stub
	}

	@Override
	public ContractGasProvider getGasProvider() {
		// TODO Auto-generated method stub
		return this.gasProvider;
	}
	
}
