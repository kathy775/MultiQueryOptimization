package mqo_gq;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;

import org.openrdf.model.Value;
import org.openrdf.query.BindingSet;
import org.openrdf.query.BooleanQuery;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.algebra.StatementPattern;
import org.openrdf.query.algebra.helpers.StatementPatternCollector;
import org.openrdf.query.parser.ParsedQuery;
import org.openrdf.query.parser.sparql.SPARQLParser;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.http.HTTPRepository;

import Common.FullQuery;
import Common.LocalQuery;
import Common.SeverInfo;
import Common.TriplePattern;

public class MultiQuery_gq {

	static int[] templateNumArr = {10};
	static int[] queryNumArr = {5};
    // 1, 获取sesame 服务器位置和repositoryID
	static ArrayList<SeverInfo> severList =  Utils.getSesameSeverArr();
/*	
 * 	static int[] templateNumArr = { 10, 10, 10, 10, 10 };
	static int[] queryNumArr = { 50, 100, 150, 200, 250 };
 *  static String[] configFileArr = {
			"E:/PengPeng/Data/WSDM/config_watdiv_10M.txt",
			"E:/PengPeng/Data/WSDM/config_watdiv_10M.txt",
			"E:/PengPeng/Data/WSDM/config_watdiv_10M.txt",
			"E:/PengPeng/Data/WSDM/config_watdiv_10M.txt",
			"E:/PengPeng/Data/WSDM/config_watdiv_10M.txt" };*/
	static int windowSize = 50;

	public static void main(String[] args) {

		try {
			// 日志文件输出
			Date day=new Date();    
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss"); 
			String currTime = df.format(day);
			PrintStream out1 = new PrintStream(new File("D:/MQO/log/" + "log_basic"+currTime+"(1).txt"));
			PrintStream out2 = new PrintStream(new File("D:/MQO/log/" + "log_basic"+currTime+"(2).txt"));
			
			for (int i = 0; i < templateNumArr.length; i++) {
				System.out.println("#########" + templateNumArr[i]+"#########");
				System.out.println("#########" + queryNumArr[i] + "#########");
//				System.out.println(configFileArr[i]);
				
				String workloadPath = "D:/MQO/query/test."+(i+1)+".sparql";
				String resultPath = "D:/MQO/log/query"+queryNumArr[i]+"_result"+currTime+".txt";
				// 第一次执行
				run(severList,workloadPath,resultPath, out1, queryNumArr[i]);
				out1.println("templateNum =" + templateNumArr[i] + ", queryNum ="+ queryNumArr[i]);
				out1.println("severList : "+severList+"\n");
				
				System.out.println("#########" + templateNumArr[i]+ "#########");
				System.out.println("#########" + queryNumArr[i] + "#########");
				System.out.println("severList : "+severList);
				
				// 第二次执行
				//run(severList,workloadPath,resultPath, out2, queryNumArr[i]);
				out2.println(" templateNum =" + templateNumArr[i] + ", queryNum ="+ queryNumArr[i]);
				out2.println(" severList : "+severList);
			}

			out1.flush();
			out1.close();
			out2.flush();
			out2.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public static void run(ArrayList<SeverInfo> severList, String workloadFileStr,String resFileStr, PrintStream out1, int queryNum) {

		String str = "";
		int count = 0;
		String[] TermArr;
		try {
		    // 1, 获取sesame 服务器位置和repositoryID
//			ArrayList<SeverInfo> severList =  Utils.getSesameSeverArr();
			
/*			InputStream in16 = new FileInputStream(new File(severList));
			Reader inr16 = new InputStreamReader(in16);
			BufferedReader br16 = new BufferedReader(inr16);

			str = br16.readLine();
			while (str != null) {
				str = str.trim();
				TermArr = str.split("\t");
				severList.add(new SeverInfo(TermArr[0], TermArr[1]));
				if (count % 10000 == 6)
					System.out.println(count);
				count++;
				str = br16.readLine();
			}
			br16.close();*/

			// 2, 保存完整查询
			ArrayList<FullQuery> allQueryList = new ArrayList<FullQuery>();
			InputStream in6 = new FileInputStream(new File(workloadFileStr));
			Reader inr6 = new InputStreamReader(in6);
			BufferedReader br6 = new BufferedReader(inr6);

			str = br6.readLine();
			int queryCount = 0;
			while (str != null) {
				str = str.trim();
				allQueryList.add(new FullQuery(str));
				if (count % 10000 == 6)
					System.out.println("count : "+count);
				count++;
				str = br6.readLine();
				queryCount ++;
				if(queryCount == queryNum){
					break;
				}
			}
			br6.close();
			int call_num = 0;
			long startTime = System.currentTimeMillis();

		    // 定位 查询对应的RDF资源
			TreeMap<String, ArrayList<Integer>> tpSourceMap = new TreeMap<String, ArrayList<Integer>>();
			for (int queryIdx = 0; queryIdx < queryNum; queryIdx++) {
				FullQuery curFullQuery = allQueryList.get(queryIdx);

				SPARQLParser parser = new SPARQLParser();
				ParsedQuery query = parser.parseQuery(curFullQuery.getSPARQLStr(), null);
				if (queryIdx % 50 == 0)
					System.out.println("============"+ " begin to localize data for full query "+ queryIdx + "=============");
				// System.out.println(curFullQuery.getSPARQLStr());

				StatementPatternCollector collector = new StatementPatternCollector();
				query.getTupleExpr().visit(collector);

				List<StatementPattern> patterns = collector.getStatementPatterns();
				ArrayList<LocalQuery> curLocalQueryList = new ArrayList<LocalQuery>();

				int var_id = 0;
				String varStr = "";
				TreeMap<String, Integer> tmpVarIDMap = new TreeMap<String, Integer>();

				String curTriplePatternStr = "";
				for (int i = 0; i < patterns.size(); i++) {
					StatementPattern curPattern = patterns.get(i);
					TriplePattern myPattern = new TriplePattern();

					curTriplePatternStr = "";
					if (!curPattern.getSubjectVar().isConstant()) {
						myPattern.setSubjectVarTag(true);
						varStr = "?" + curPattern.getSubjectVar().getName();

						if (!tmpVarIDMap.containsKey(varStr)) {
							tmpVarIDMap.put(varStr, var_id);
							curFullQuery.getVarIDMap().put(varStr, var_id);
							curFullQuery.getIDVarMap().put(var_id, varStr);
							var_id++;
						}
						myPattern.setSubjectStr(varStr);
						curTriplePatternStr += varStr + "\t";
					} else {
						myPattern.setSubjectVarTag(false);
						curTriplePatternStr += "<"
								+ curPattern.getSubjectVar().getValue()
										.toString() + ">\t";
						myPattern.setSubjectStr("<"
								+ curPattern.getSubjectVar().getValue()
										.toString() + ">");
					}

					if (!curPattern.getPredicateVar().isConstant()) {
						myPattern.setPredicateVarTag(true);
						varStr = "?" + curPattern.getPredicateVar().getName();
						if (!tmpVarIDMap.containsKey(varStr)) {
							tmpVarIDMap.put(varStr, var_id);
							curFullQuery.getVarIDMap().put(varStr, var_id);
							curFullQuery.getIDVarMap().put(var_id, varStr);
							var_id++;
						}
						curTriplePatternStr += varStr + "\t";
						myPattern.setPredicateStr(varStr);
					} else {
						myPattern.setPredicateVarTag(false);
						curTriplePatternStr += "<"+ curPattern.getPredicateVar().getValue().toString() + ">\t";
						myPattern.setPredicateStr("<"+ curPattern.getPredicateVar().getValue().toString() + ">");
					}

					if (!curPattern.getObjectVar().isConstant()) {
						myPattern.setObjectVarTag(true);
						varStr = "?" + curPattern.getObjectVar().getName();

						if (!tmpVarIDMap.containsKey(varStr)) {
							tmpVarIDMap.put(varStr, var_id);
							curFullQuery.getVarIDMap().put(varStr, var_id);
							curFullQuery.getIDVarMap().put(var_id, varStr);
							var_id++;
						}
						curTriplePatternStr += varStr + "\t";
						myPattern.setObjectStr(varStr);
					} else {
						myPattern.setObjectVarTag(false);
						String tmpConstantStr = curPattern.getObjectVar()
								.getValue().toString();
						if (!tmpConstantStr.startsWith("\"")) {
							curTriplePatternStr += "<" + tmpConstantStr + ">\t";
							myPattern.setObjectStr("<" + tmpConstantStr + ">");
						} else {
							tmpConstantStr = tmpConstantStr.replace("^^<http://www.w3.org/2001/XMLSchema#string>","");
							curTriplePatternStr += tmpConstantStr + "\t";
							myPattern.setObjectStr(tmpConstantStr);
						}
					}
					curTriplePatternStr = curTriplePatternStr.trim();
					curTriplePatternStr += ".";
					curFullQuery.addTriplePattern(myPattern);

					ArrayList<Integer> curSourceList = new ArrayList<Integer>();

					if (tpSourceMap.containsKey(myPattern.getSignature())) {
						curSourceList = tpSourceMap.get(myPattern.getSignature());
					} else {
						for (int k = 0; k < severList.size(); k++) {
							SeverInfo tmpSeverInfo = severList.get(k);
							Repository repo = new HTTPRepository(tmpSeverInfo.getSesameServer(),tmpSeverInfo.getRepositoryID());
							repo.initialize();

							RepositoryConnection con = repo.getConnection();

							String askQueryStr = "ask { " + curTriplePatternStr+ "}";
							// System.out.println(askQueryStr +
							// "---------");

							BooleanQuery booleanQuery = con.prepareBooleanQuery(QueryLanguage.SPARQL,askQueryStr);

							boolean ask_res = booleanQuery.evaluate();
							if (ask_res) {
								curSourceList.add(k);
							}

							con.close();
						}
						tpSourceMap.put(myPattern.getSignature(), curSourceList);
					}

					if (curSourceList.size() == 1) {
						int tag = 0;
						for (int k = 0; k < curLocalQueryList.size(); k++) {
							if (curLocalQueryList.get(k).getSourceList().equals(curSourceList)) {
								curLocalQueryList.get(k).addTriplePattern(myPattern);
								tag = 1;
								break;
							}
						}

						if (0 == tag) {
							LocalQuery curLocalQuery = new LocalQuery();
							curLocalQuery.getSourceList().addAll(curSourceList);
							curLocalQuery.addTriplePattern(myPattern);
							curLocalQueryList.add(curLocalQuery);
						}
					} else {
						LocalQuery curLocalQuery = new LocalQuery();
						curLocalQuery.getSourceList().addAll(curSourceList);
						curLocalQuery.addTriplePattern(myPattern);
						curLocalQueryList.add(curLocalQuery);
					}

				}
				curFullQuery.getLocalQueryList().addAll(curLocalQueryList);
			}

			for (int queryIdx = 0; queryIdx < queryNum; queryIdx++) {
				FullQuery curFullQuery = allQueryList.get(queryIdx);
				TreeMap<String, Integer> curVarIDMap = curFullQuery.getVarIDMap();
				
				// 开始查询
				System.out.println("*********** begin to find results of full query " + queryIdx+ "***********");
				System.out.println(curFullQuery.getSPARQLStr());

				for (int localQueryIdx = 0; localQueryIdx < curFullQuery.getLocalQueryList().size(); localQueryIdx++) {

					LocalQuery curLocalQuery = curFullQuery.getLocalQuery(localQueryIdx);
					String curLocalQueryStr = curLocalQuery.toSPARQLString();
					// System.out.println(curLocalQueryStr);
					ArrayList<Integer> curSourceList = curLocalQuery.getSourceList();

					if (curSourceList.size() == 0)
						continue;
					// 在包含的资源内查询
					for (int k = 0; k < curSourceList.size(); k++) {
						call_num++;
						if (k >= 0) {
							// continue;
						}
						SeverInfo tmpSeverInfo = severList.get(curSourceList.get(k));
						Repository repo = new HTTPRepository(tmpSeverInfo.getSesameServer(),tmpSeverInfo.getRepositoryID());
						repo.initialize();
						RepositoryConnection con = repo.getConnection();
						TupleQuery tupleQuery = con.prepareTupleQuery(QueryLanguage.SPARQL, curLocalQueryStr);

						TupleQueryResult result = tupleQuery.evaluate();
						List<String> bindingNames = result.getBindingNames();
						int res_count = 0;
						while (result.hasNext()) {
							if (res_count++ == 100000)
								break;
							BindingSet bindingSet = result.next();
							String[] curRes = new String[curVarIDMap.size()];
							Arrays.fill(curRes, "");
							for (int i = 0; i < bindingNames.size(); i++) {
								Value val_1 = bindingSet.getValue(bindingNames.get(i));
								int pos = curVarIDMap.get("?"+ bindingNames.get(i));
								if (val_1 != null) {
									curRes[pos] = val_1.toString();
								} else {
									curRes[pos] = "";
								}
							}
							curLocalQuery.addResult(curRes);
						}
						con.close();
					}
				}
			}

			for (int queryIdx = 0; queryIdx < queryNum; queryIdx++) {

				FullQuery curFullQuery = allQueryList.get(queryIdx);
				curFullQuery.setResultList(curFullQuery.getLocalQuery(0).getResultList());

				for (int i = 1; i < curFullQuery.getLocalQueryList().size(); i++) {
					if (curFullQuery.getResultList().size() > 0) {
						LocalQuery curLocalQuery = curFullQuery.getLocalQuery(i);

						if (curLocalQuery.getResultList().size() > 0) {
							int joining_pos = 0;
							for (int k = 0; k < curFullQuery.getVarIDMap().size(); k++) {
								if (!curFullQuery.getResult(0)[k].equals("")&& !curLocalQuery.getResult(0)[k].equals("")) {
									joining_pos = k;
									break;
								}
							}

							curFullQuery.Join(curLocalQuery.getResultList(),joining_pos);
						}
					}
				}
			}

			long endTime = System.currentTimeMillis();
			
			// 输出每个查询的结果
			PrintStream out = new PrintStream(new File(resFileStr));
			for (int queryIdx = 0; queryIdx < queryNum; queryIdx++) {
				FullQuery curFullQuery = allQueryList.get(queryIdx);
				out.println();
				out.println("============there are "+ curFullQuery.getResultList().size()+ " results for query " + (queryIdx+1) + " =============\n");
				out.println();
				for (int i = 0; i < curFullQuery.getResultList().size(); i++) {
					for (int j = 0; j < curFullQuery.getResultList().get(i).length; j++) {
						out.print(curFullQuery.getIDVarMap().get(j) + "\t"+ curFullQuery.getResultList().get(i)[j] + " | \t");
					}
					out.println();
				}
			}

			out1.println("evaluation time = " + (endTime - startTime));
			out1.println("number of remote call = " + call_num);
			System.out.println("end!!!");

			out.flush();
			out.close();
		} catch (RepositoryException e) {
			e.printStackTrace();
		} catch (MalformedQueryException e) {
			e.printStackTrace();
		} catch (QueryEvaluationException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
