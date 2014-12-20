package main.java;

import java.util.ArrayList;
import java.util.List;

import main.java.bolts.OutputBolt;
import main.java.bolts.WorkberchCartesianBolt;
import main.java.spouts.SimpleSpout;
import main.java.utils.constants.WorkberchConstants;
import redis.clients.jedis.Jedis;
import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.tuple.Values;

public class CartesianPlainTopologyMain {

	public static void main(final String[] args) {
		WorkberchConstants.GUID = args[0];
		WorkberchConstants.OUTPUT_PATH = args[1];
		WorkberchConstants.INPUT_PATH = args[2];
		
		final Jedis jedis = new Jedis("localhost");
		jedis.flushAll();
		jedis.close();
		
		final TopologyBuilder builder = new TopologyBuilder();

		final List<Values> valuesToEmit = new ArrayList<Values>();
		valuesToEmit.add(new Values("FirstValue"));
		valuesToEmit.add(new Values("SecondValue"));
		valuesToEmit.add(new Values("ThirdValue"));
		valuesToEmit.add(new Values("FourthValue"));

		final List<String> outputFields1 = new ArrayList<String>();
		outputFields1.add("dummyField1");

		final List<String> outputFields2 = new ArrayList<String>();
		outputFields2.add("dummyField2");

		builder.setSpout("input1", new SimpleSpout(outputFields1, valuesToEmit), 1);
		builder.setSpout("input2", new SimpleSpout(outputFields2, valuesToEmit), 1);

		final List<String> cartesianFields = new ArrayList<String>();
		cartesianFields.add("dummyField1");
		cartesianFields.add("dummyField2");

		builder.setBolt("cartesianTestBolt", new WorkberchCartesianBolt(cartesianFields), 3).allGrouping("input1").shuffleGrouping("input2");
		
		builder.setBolt("dummyField1", new OutputBolt(false) , 1).shuffleGrouping("cartesianTestBolt");
		
		builder.setBolt("dummyField2", new OutputBolt(false) , 1).shuffleGrouping("cartesianTestBolt");

		final Config conf = new Config();
		conf.setDebug(false);

		final LocalCluster cluster = new LocalCluster();
		cluster.submitTopology("cartesianTopology", conf, builder.createTopology());
	}

}
