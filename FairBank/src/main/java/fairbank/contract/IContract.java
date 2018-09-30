package fairbank.contract;

import org.web3j.tx.gas.ContractGasProvider;

public interface IContract {

	public ContractGasProvider getGasProvider();
}
