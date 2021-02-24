package net.sansa_stack.rdf.common.io.hadoop.rdf.turtle;

import net.sansa_stack.rdf.common.io.hadoop.rdf.base.FileInputFormatRdfBase;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;

public class FileInputFormatTurtleTriple
        extends FileInputFormatRdfBase<Triple>
{
    public FileInputFormatTurtleTriple() {
        super(Lang.TURTLE);
    }

    @Override
    public RecordReader<LongWritable, Triple> createRecordReaderActual(InputSplit inputSplit, TaskAttemptContext context) {
        return new RecordReaderTurtleTriple();
    }
}
