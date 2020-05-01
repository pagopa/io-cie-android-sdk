
#include "RSA.h"
#include <openssl/bn.h>
#include "util.h"

#ifdef WIN32

class init_rsa {
public:
	BCRYPT_ALG_HANDLE algo;
	init_rsa() {
		if (BCryptOpenAlgorithmProvider(&algo, BCRYPT_RSA_ALGORITHM, MS_PRIMITIVE_PROVIDER, 0) != 0)
			throw logged_error("Errore nell'inizializzazione dell'algoritmo RSA");
	}
	~init_rsa() {
		BCryptCloseAlgorithmProvider(algo, 0);
	}
} algo_rsa;

CRSA::CRSA(ByteArray &mod, ByteArray &exp)
{
	KeySize = mod.size();
	ByteDynArray KeyData(sizeof(BCRYPT_RSAKEY_BLOB) + mod.size() + exp.size());
	BCRYPT_RSAKEY_BLOB *rsaImpKey = (BCRYPT_RSAKEY_BLOB *)KeyData.data();
	rsaImpKey->Magic = BCRYPT_RSAPUBLIC_MAGIC;
	rsaImpKey->BitLength = (ULONG)(KeySize << 3);
	rsaImpKey->cbModulus = (ULONG)KeySize;
	rsaImpKey->cbPublicExp = (ULONG)exp.size();
	rsaImpKey->cbPrime1 = 0;
	rsaImpKey->cbPrime2 = 0;

	KeyData.copy(exp, sizeof(BCRYPT_RSAKEY_BLOB));
	KeyData.rightcopy(mod);

	this->key = nullptr;
	if (BCryptImportKeyPair(algo_rsa.algo, nullptr, BCRYPT_RSAPUBLIC_BLOB, &this->key, KeyData.data(), (ULONG)KeyData.size(), BCRYPT_NO_KEY_VALIDATION) != 0)
		throw logged_error("Errore nella creazione della chiave RSA");
}

void CRSA::GenerateKey(DWORD size, ByteDynArray &module, ByteDynArray &pubexp, ByteDynArray &privexp)
{
	init_func
	throw logged_error("Non supportato");
}

CRSA::~CRSA(void)
{
	if (key != nullptr)
		BCryptDestroyKey(key);
}

ByteDynArray CRSA::RSA_PURE(ByteArray &data)
{
	ULONG size = 0;
	if (BCryptEncrypt(key, data.data(), (ULONG)data.size(), nullptr, nullptr, 0, nullptr, 0, &size, 0) != 0)
		throw logged_error("Errore nella cifratura RSA");
	ByteDynArray resp(size);
	if (BCryptEncrypt(key, data.data(), (ULONG)data.size(), nullptr, nullptr, 0, resp.data(), (ULONG)resp.size(), &size, 0) != 0)
		throw logged_error("Errore nella cifratura RSA");

	ER_ASSERT(size == KeySize, "Errore nella lunghezza dei dati per operazione RSA")
	return resp;
}

#else

DWORD CRSA::GenerateKey(DWORD size, ByteDynArray &module, ByteDynArray &pubexp, ByteDynArray &privexp) 
{
	init_func
	keyPriv = RSA_new();
	auto BNpubexp = BN_new();
	BN_set_word(BNpubexp, 65537);
	RSA_generate_key_ex(keyPriv, size, BNpubexp, nullptr);
    const BIGNUM *n = RSA_get0_n(keyPriv);
    const BIGNUM *e = RSA_get0_e(keyPriv);;
    const BIGNUM *d = RSA_get0_d(keyPriv);;
    
    
	module.resize(BN_num_bytes(n));
	BN_bn2bin(n, module.data());
	privexp.resize(BN_num_bytes(d));
	BN_bn2bin(d, privexp.data());
	pubexp.resize(BN_num_bytes(e));
	BN_bn2bin(e, pubexp.data());

    BN_clear_free(BNpubexp);
    
    return(S_OK);
	exit_func
    return(-1);
}

ByteArray modulusBa;
ByteArray exponentBa;

CRSA::CRSA(ByteArray &mod,ByteArray &exp)
{
    modulusBa = mod;
    exponentBa = exp;
    
//    ByteDynArray modBa(mod.size() + 1);
//    modBa.fill(0);
//    modBa.rightcopy(mod);
//
//    ByteDynArray expBa(exp.size() + 1);
//    expBa.fill(0);
//    expBa.rightcopy(exp);
    
    
	KeySize = mod.size();
	keyPriv = RSA_new();
    BIGNUM *n = BN_new();;
    BIGNUM *e = BN_new();;
    BIGNUM *d = BN_new();
    
    n = BN_bin2bn(mod.data(), (int)mod.size(), n);
    e = BN_bin2bn(exp.data(), (int)exp.size(), e);
    RSA_set0_key(keyPriv, n, e, d);
    	
}

CRSA::~CRSA(void)
{
	if (keyPriv!=nullptr)
		RSA_free(keyPriv);
}

ByteDynArray CRSA::RSA_PURE(ByteArray &data)
{
    ByteDynArray resp(RSA_size(keyPriv));
    int SignSize = RSA_public_encrypt((int)data.size(), data.data(), resp.data(), keyPriv, RSA_NO_PADDING);

    ER_ASSERT(SignSize == KeySize, "Errore nella lunghezza dei dati per operazione RSA")

//    printf("\nRSA resp1: %s\n", dumpHexData(resp).c_str());  // DEBUG
    
    return resp;
}
#endif
