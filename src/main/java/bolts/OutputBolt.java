package main.java.bolts;

import static main.java.utils.constants.WorkberchConstants.XML_EXT;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import main.java.utils.WorkberchTuple;
import main.java.utils.constants.WorkberchConstants;
import main.java.utils.redis.RedisHandeler;

import org.apache.commons.io.FileUtils;

import backtype.storm.topology.BasicOutputCollector;

import com.google.common.base.Throwables;

public class OutputBolt extends WorkberchOrderBolt {

	private static final long serialVersionUID = 1L;

	private final List<WorkberchTuple> tuplesToWrite = new ArrayList<WorkberchTuple>();

	public OutputBolt(final Boolean ordered) {
		super(new ArrayList<String>(), ordered);

		try {
			FileUtils.deleteDirectory(new File(WorkberchConstants.OUTPUT_PATH));
			FileUtils.forceMkdir(new File(WorkberchConstants.OUTPUT_PATH));
		} catch (final IOException e) {
			Throwables.propagate(e);
		}
	}

	@Override
	public void executeOrdered(final WorkberchTuple input, final BasicOutputCollector collector, final boolean lastValues) {
		tuplesToWrite.add(input);
		if (lastValues) {
			final File file = new File(WorkberchConstants.OUTPUT_PATH + getBoltId() + XML_EXT);
			try {
				if (!file.exists()) {
					file.createNewFile();
				}

				final PrintWriter bufferWritter = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));

				for (final WorkberchTuple inputToWrite : tuplesToWrite) {
					for (final String field : inputToWrite.getFields()) {
						bufferWritter.println("Valor " + field + " - " + inputToWrite.getValues().get(field));
					}
				}

				bufferWritter.close();
				RedisHandeler.setStateFinished(getBoltId());
			} catch (final IOException e) {
				Throwables.propagate(e);
			}
		}
	}

}
